// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.data.repository

import android.content.SharedPreferences
import android.content.Context
import kotlinx.coroutines.withTimeoutOrNull
import wang.lucian.quietcalls.data.rules.CallRuleSettings
import wang.lucian.quietcalls.data.rules.CallRuleMatcher
import wang.lucian.quietcalls.data.rules.OfflinePhoneLocation
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import wang.lucian.quietcalls.data.PhoneNumberNormalizer
import wang.lucian.quietcalls.data.PhoneNumberRuleMatcher
import wang.lucian.quietcalls.data.entity.ResultType
import wang.lucian.quietcalls.data.model.CommunityStatus
import wang.lucian.quietcalls.data.preferences.CommunityReportPreferences
import wang.lucian.quietcalls.data.query.CommunityQueryEpoch
import wang.lucian.quietcalls.data.query.OFFICIAL_BACKEND_ID
import wang.lucian.quietcalls.data.query.decodeCommunityQueryMetadata
import wang.lucian.quietcalls.data.query.BackendQueryResponse
import wang.lucian.quietcalls.data.query.QueryBackendProvider
import wang.lucian.quietcalls.data.query.QueryBackendSnapshot
import wang.lucian.quietcalls.data.remote.PhoneLocationInfo
import wang.lucian.quietcalls.viewmodel.SettingViewModel
import kotlin.time.Duration.Companion.milliseconds

data class CheckResult(
    val shouldBlock: Boolean,
    val label: String,
    val resultType: ResultType,
    val localCost: Long,
    val networkCost: Long,
    val locationInfo: PhoneLocationInfo? = null,
    val locationLookupAttempted: Boolean = false,
    /** true 表示用户规则要求强制拦截，不受“仅提示”等全局策略影响。 */
    val forceBlock: Boolean = false,
    val querySource: String? = null,
    /** 联网结果所属的稳定 Backend ID；纯本地结果或联网失败时为 null。 */
    val queryBackendId: String? = null,
    val feedbackToken: String? = null,
    /** 离线身份资料仅提供名称，Directory 展示不依赖是否拦截。 */
    val isIdentityProfile: Boolean = false,
    val communityStatus: CommunityStatus = CommunityStatus.UNKNOWN,
    val communityBlockCandidate: Boolean = false,
    val feedbackPhone: String? = null,
    val feedbackTokenExpiresAt: Long? = null,
    val communityEpoch: Long = -1,
    val communityReceivedAtElapsedMs: Long = -1,
)

class SpamNumberRepository : KoinComponent {
    companion object {
        private const val TAG = "SpamNumberRepository"
        private const val HUB_IDENTITY_SOURCE = "pixel_text_hub"
        private const val QUERY_REUSE_TTL_MS = 60_000L
        private const val QUERY_REUSE_MAX_ENTRIES = 32
    }

    private val context: Context by inject()
    private val syncRepository: SyncRepository by inject()
    private val queryRepository: QueryRepository by inject()
    private val queryBackendProvider: QueryBackendProvider by inject()
    private val prefs: SharedPreferences by inject()
    private val communityPreferences: CommunityReportPreferences by inject()
    private val communityEpoch: CommunityQueryEpoch by inject()
    private val userListRepository: UserListRepository by inject()
    private val contactRepository: ContactRepository by inject()
    private val queryReuseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queryReuseMutex = Mutex()
    private val completedNetworkQueries = LinkedHashMap<QueryReuseKey, CachedNetworkAttempt>(
        QUERY_REUSE_MAX_ENTRIES,
        0.75f,
        true,
    )
    private val inFlightNetworkQueries = mutableMapOf<QueryReuseKey, Deferred<NetworkAttempt>>()

    /**
     * 仅发起联网查询，跳过本地数据库检查。
     * 用于手动重试联网查询超时的记录。超时限制使用用户设置。
     */
    suspend fun queryNetwork(phoneNumber: String): BackendQueryResponse {
        // 历史记录重试也必须复用统一国际号码语义，并在任何标准化或 Backend 访问前拦截。
        if (PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber)) {
            Log.i(TAG, "Explicit international retry skipped")
            throw BackendBlockedException()
        }
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        return withContext(Dispatchers.IO) {
            withTimeout(networkTimeoutMs().milliseconds) {
                queryRepository.queryNumber(phone)
            }
        }
    }

    /** 本地规则优先，地域白名单仅豁免陌生人规则，绝不覆盖骚扰结论。 */
    suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val started = SystemClock.elapsedRealtime()
        userListRepository.migrateLegacyWhiteRegions(prefs)
        val rules = CallRuleSettings.read(prefs)
        val contact = contactRepository.screeningContactStatus(phoneNumber)
        if (contact == true) return CheckResult(false, "通讯录联系人", ResultType.PASS, 0, 0)
        if (rules.allowInternal && CallRuleMatcher.internalNumber(phoneNumber))
            return CheckResult(false, "集团内部短号", ResultType.WHITE_LIST, 0, 0)
        if (CallRuleMatcher.allowedNumber(phoneNumber, rules.allowedNumbers))
            return CheckResult(false, "号码通行名单", ResultType.WHITE_LIST, 0, 0)
        val white = userListRepository.findWhiteListMatch(phoneNumber)
        if (white != null) return CheckResult(false, white.remark.orEmpty(), ResultType.WHITE_LIST, 0, 0)
        // 未能可靠检查通讯录时保守放行，不能将查询失败误当作陌生人并拒接联系人。
        if (contact == null) return CheckResult(false, "通讯录检查不可用，已放行", ResultType.PASS_BUT_NOTIFY, 0, 0)
        val black = userListRepository.findBlackListMatch(phoneNumber)
        if (black != null) return CheckResult(true, black.remark.orEmpty(), ResultType.BLACK_LIST, 0, 0, forceBlock = black.forceBlock)
        val foreign = CallRuleMatcher.foreignKind(phoneNumber)
        fun ruleBlock(reason: String, location: PhoneLocationInfo? = null) = CheckResult(
            true, reason, ResultType.INTERCEPT, SystemClock.elapsedRealtime()-started, 0,
            locationInfo = location,
        )
        if (foreign == 1 && rules.blockInternational) return ruleBlock("规则：境外来电")
        if (foreign == 2 && rules.blockHongKongMacaoTaiwan) return ruleBlock("规则：港澳台来电")
        CallRuleMatcher.specialBlock(phoneNumber, rules)?.let { return ruleBlock("规则：" + it) }
        val localLocation = withContext(Dispatchers.IO) { OfflinePhoneLocation.lookup(context, phoneNumber) }
        val localRegionBlack = userListRepository.findBlackListLocationMatch(localLocation)
        if (localRegionBlack != null) return CheckResult(
            true, localRegionBlack.remark ?: "规则：归属地黑名单", ResultType.BLACK_LIST,
            SystemClock.elapsedRealtime()-started, 0, locationInfo = localLocation,
            forceBlock = localRegionBlack.forceBlock,
        )
        val locallyAllowed = localLocation != null &&
            (CallRuleMatcher.allowedRegion(localLocation.province, localLocation.city, rules.allowedRegions) ||
                userListRepository.findWhiteListLocationMatch(localLocation) != null)
        // 非白名单地域的陌生来电可直接本地决策，不必等待在线识别。
        if (rules.blockStrangers && !locallyAllowed && (localLocation != null || rules.allowedRegions.isBlank()))
            return ruleBlock("规则：陌生号码，未命中通行地域", localLocation)
        val remaining = (4000L-(SystemClock.elapsedRealtime()-started)).coerceAtLeast(1L)
        val base = withTimeoutOrNull(remaining) { checkExistingSpam(phoneNumber, forceNetworkQuery) }
            ?: CheckResult(false, "", ResultType.NETWORK_TIMEOUT, SystemClock.elapsedRealtime()-started, 0)
        if (base.shouldBlock || base.resultType == ResultType.WHITE_LIST) return base
        val location = localLocation ?: base.locationInfo
        // 推销分类独立于 is_spam 字段，例如来源只返回了“营销推广”标签时也能命中。
        if (rules.blockMarketing && CallRuleMatcher.marketing(base.label))
            return base.copy(shouldBlock = true, label = "规则：推销电话 · "+base.label, resultType = ResultType.INTERCEPT)
        val allowedRegion = location != null &&
            (CallRuleMatcher.allowedRegion(location.province, location.city, rules.allowedRegions) ||
                userListRepository.findWhiteListLocationMatch(location) != null)
        if (rules.blockStrangers && !allowedRegion) return ruleBlock(
            if (location == null) "规则：陌生号码，归属地未知" else "规则：陌生号码，未命中通行地域", location,
        )
        return base.copy(locationInfo = location, label = base.label.ifBlank {
            if (allowedRegion) "地域通行，未发现本地骚扰标记" else ""
        })
    }

    private suspend fun checkExistingSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
        val start = System.currentTimeMillis()
        val phone = PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        var localCost: Long
        var networkCost: Long

        val whiteMatch = userListRepository.findWhiteListMatch(phoneNumber)
        if (whiteMatch != null) {
            Log.i(TAG, "White list hit")
            return CheckResult(false, whiteMatch.remark ?: "", ResultType.WHITE_LIST, 0, 0)
        }

        val blackMatch = userListRepository.findBlackListMatch(phoneNumber)
        if (blackMatch != null) {
            Log.i(TAG, "Black list hit")
            return CheckResult(
                shouldBlock = true,
                label = blackMatch.remark ?: "",
                resultType = ResultType.BLACK_LIST,
                localCost = 0,
                networkCost = 0,
                forceBlock = blackMatch.forceBlock
            )
        }

        if (PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber)) {
            Log.i(TAG, "Explicit international number skipped")
            return CheckResult(
                shouldBlock = false,
                label = "",
                resultType = ResultType.PASS_BUT_NOTIFY,
                localCost = System.currentTimeMillis() - start,
                networkCost = 0,
                locationLookupAttempted = false,
            )
        }

        val db = syncRepository.getDb()
        if (db != null) {
            val spamNumber = withContext(Dispatchers.IO) {
                try {
                    db.spamNumberDao().search(phone)
                } catch (_: Exception) {
                    Log.w(TAG, "Local lookup failed")
                    null
                } finally {
                    db.close()
                }
            }
            localCost = System.currentTimeMillis() - start
            if (spamNumber != null) {
                val tagBlackMatch = userListRepository.findBlackListTagMatch(spamNumber.tag)
                if (tagBlackMatch != null) {
                    Log.i(TAG, "Black list tag hit")
                    return CheckResult(
                        shouldBlock = true,
                        label = spamNumber.tag,
                        resultType = ResultType.BLACK_LIST,
                        localCost = localCost,
                        networkCost = 0,
                        forceBlock = tagBlackMatch.forceBlock
                    )
                }

                val tagWhiteMatch = userListRepository.findWhiteListTagMatch(spamNumber.tag)
                if (tagWhiteMatch != null) {
                    Log.i(TAG, "White list tag hit")
                    return CheckResult(false, spamNumber.tag, ResultType.WHITE_LIST, localCost, 0)
                }
                Log.i(TAG, "Local hit: cost=${localCost}ms")
                // Hub 机构资料不是骚扰标记；沿用离线命中即返回，并保留前面的用户名单优先级。
                if (spamNumber.source == HUB_IDENTITY_SOURCE) {
                    if (CallRuleSettings.read(prefs).blockMarketing && CallRuleMatcher.marketing(spamNumber.tag))
                        return CheckResult(true, "规则：推销电话 · "+spamNumber.tag, ResultType.INTERCEPT, localCost, 0)
                    return CheckResult(
                        shouldBlock = false,
                        label = spamNumber.tag,
                        resultType = ResultType.PASS_BUT_NOTIFY,
                        localCost = localCost,
                        networkCost = 0,
                        isIdentityProfile = true,
                    )
                }
                return CheckResult(true, spamNumber.tag, ResultType.INTERCEPT, localCost, 0)
            }
        } else {
            localCost = System.currentTimeMillis() - start
        }

        if (localCost > 100) {
            Log.w(TAG, "Local lookup too slow: ${localCost}ms")
        }

        val noNetworkQuery = prefs.getBoolean("no_network_query", false)
        if (noNetworkQuery && !forceNetworkQuery) {
            Log.i(TAG, "Offline query only enabled")
            return CheckResult(false, "", ResultType.PASS_BUT_NOTIFY, localCost, 0)
        }

        var networkAttempt: NetworkAttempt? = null
        return withContext(Dispatchers.IO) {
            val backendResponse = try {
                val reusedAttempt = if (forceNetworkQuery) {
                    ReusedNetworkAttempt(
                        attempt = executeNetworkQuery(
                            phone = phone,
                            snapshot = null,
                            timeoutMs = networkTimeoutMs(),
                        ),
                        reused = false,
                    )
                } else {
                    queryNetworkForCheck(phone)
                }
                networkAttempt = reusedAttempt.attempt
                networkCost = reusedAttempt.attempt.costMs
                if (reusedAttempt.reused) {
                    Log.d(TAG, "Network query result reused: cost=${networkCost}ms")
                }
                reusedAttempt.attempt.result.getOrThrow().also {
                    Log.i(
                        TAG,
                        if (reusedAttempt.reused) {
                            "Network query result reused: cost=${networkCost}ms"
                        } else {
                            "Network query succeeded: cost=${networkCost}ms"
                        },
                    )
                }
            } catch (exception: TimeoutCancellationException) {
                networkCost = networkAttempt?.costMs ?: 0L
                return@withContext buildNetworkFailureResult(
                    exception = exception,
                    localCost = localCost,
                    networkCost = networkCost,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                networkCost = networkAttempt?.costMs ?: 0L
                return@withContext buildNetworkFailureResult(
                    exception = exception,
                    localCost = localCost,
                    networkCost = networkCost,
                )
            }

            // 名单与结果组装属于本地后处理，不应被误记为服务端响应错误。
            buildNetworkResult(backendResponse, localCost, networkCost)
        }
    }

    /**
     * 复用同一来电窗口内的联网结果，并合并尚未完成的相同请求。
     *
     * 缓存键绑定 Backend 激活代次与超时设置，避免 Backend 切换后误用旧结果。
     */
    private suspend fun queryNetworkForCheck(phone: String): ReusedNetworkAttempt {
        val timeoutMs = networkTimeoutMs()
        val snapshot = queryBackendProvider.snapshot()
            ?: return ReusedNetworkAttempt(
                attempt = NetworkAttempt(
                    result = Result.failure(BackendBlockedException()),
                    costMs = 0L,
                ),
                reused = false,
            )
        val key = currentReuseKey(phone, snapshot, timeoutMs)
        val now = SystemClock.elapsedRealtime()
        val lookup = queryReuseMutex.withLock {
            removeExpiredNetworkQueries(now)
            completedNetworkQueries[key]?.let { cached ->
                return@withLock ReuseLookup.Completed(cached.attempt)
            }
            inFlightNetworkQueries[key]?.let { deferred ->
                return@withLock ReuseLookup.InFlight(deferred, joined = true)
            }
            if (inFlightNetworkQueries.size >= QUERY_REUSE_MAX_ENTRIES) {
                return@withLock ReuseLookup.Direct(snapshot, timeoutMs)
            }

            val deferred = queryReuseScope.async {
                executeNetworkQuery(phone, snapshot, timeoutMs)
            }
            inFlightNetworkQueries[key] = deferred
            observeNetworkQuery(key, deferred)
            ReuseLookup.InFlight(deferred, joined = false)
        }

        return when (lookup) {
            is ReuseLookup.Completed -> ReusedNetworkAttempt(
                attempt = lookup.attempt,
                reused = true,
            )

            is ReuseLookup.InFlight -> {
                if (lookup.joined) {
                    Log.d(TAG, "Joining in-flight network query")
                }
                ReusedNetworkAttempt(
                    attempt = lookup.deferred.await(),
                    reused = lookup.joined,
                )
            }

            is ReuseLookup.Direct -> {
                Log.w(TAG, "Network query reuse capacity reached")
                ReusedNetworkAttempt(
                    attempt = executeNetworkQuery(phone, lookup.snapshot, lookup.timeoutMs),
                    reused = false,
                )
            }
        }
    }

    /** 独立观察共享请求，确保所有调用方取消后仍能清理进行中状态并发布缓存。 */
    private fun observeNetworkQuery(
        key: QueryReuseKey,
        deferred: Deferred<NetworkAttempt>,
    ) {
        queryReuseScope.launch {
            val attempt = try {
                deferred.await()
            } catch (_: CancellationException) {
                queryReuseMutex.withLock {
                    if (inFlightNetworkQueries[key] === deferred) {
                        inFlightNetworkQueries.remove(key)
                    }
                }
                return@launch
            }

            queryReuseMutex.withLock {
                if (inFlightNetworkQueries[key] !== deferred) return@withLock
                inFlightNetworkQueries.remove(key)
                completedNetworkQueries[key] = CachedNetworkAttempt(
                    attempt = attempt,
                    completedAtElapsedMs = SystemClock.elapsedRealtime(),
                )
                trimCompletedNetworkQueries()
            }
        }
    }

    private suspend fun executeNetworkQuery(
        phone: String,
        snapshot: QueryBackendSnapshot?,
        timeoutMs: Long,
    ): NetworkAttempt {
        val startedAt = SystemClock.elapsedRealtime()
        val result = try {
            Result.success(
                withTimeout(timeoutMs.milliseconds) {
                    if (snapshot == null) {
                        queryRepository.queryNumber(phone)
                    } else {
                        queryRepository.queryNumber(phone, snapshot)
                    }
                },
            )
        } catch (exception: TimeoutCancellationException) {
            Result.failure(exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
        return NetworkAttempt(
            result = result,
            costMs = SystemClock.elapsedRealtime() - startedAt,
        )
    }

    private fun currentReuseKey(
        phone: String,
        snapshot: QueryBackendSnapshot?,
        timeoutMs: Long,
    ): QueryReuseKey {
        return QueryReuseKey(
            phone = phone,
            backendId = snapshot?.backendId,
            activationId = snapshot?.activationId,
            timeoutMs = timeoutMs,
            communityEpoch = if (snapshot?.backendId == OFFICIAL_BACKEND_ID) communityEpoch.current() else 0,
        )
    }

    private fun removeExpiredNetworkQueries(now: Long) {
        val iterator = completedNetworkQueries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.completedAtElapsedMs >= QUERY_REUSE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    private fun trimCompletedNetworkQueries() {
        while (completedNetworkQueries.size > QUERY_REUSE_MAX_ENTRIES) {
            val eldestKey = completedNetworkQueries.entries.firstOrNull()?.key ?: return
            completedNetworkQueries.remove(eldestKey)
        }
    }

    /** 失效时不取消共享请求，保留其 Provider 结论，仅阻止旧社区增强回灌。 */
    suspend fun invalidateOfficialQueryReuse() {
        queryReuseMutex.withLock {
            completedNetworkQueries.keys.removeAll { it.backendId == OFFICIAL_BACKEND_ID }
            inFlightNetworkQueries.keys.removeAll { it.backendId == OFFICIAL_BACKEND_ID }
        }
    }

    /** 系统最终展示／处置前再次核对代次，撤回或关闭开关不能留下旧拦截决定。 */
    fun currentCommunityResult(result: CheckResult): CheckResult {
        val fresh = communityEpoch.isCurrent(result.communityEpoch, result.communityReceivedAtElapsedMs)
        val cancelCommunityBlock = result.communityBlockCandidate &&
            (!fresh || !communityPreferences.blockEnabled.value)
        return result.copy(
            communityStatus = if (fresh) result.communityStatus else CommunityStatus.UNKNOWN,
            shouldBlock = if (cancelCommunityBlock) false else result.shouldBlock,
            resultType = if (cancelCommunityBlock) ResultType.PASS_BUT_NOTIFY else result.resultType,
            communityBlockCandidate = result.communityBlockCandidate && !cancelCommunityBlock,
        )
    }

    private suspend fun buildNetworkResult(
        backendResponse: BackendQueryResponse,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val base = buildProviderNetworkResult(backendResponse, localCost, networkCost)
        val metadata = decodeCommunityQueryMetadata(
            backendResponse.response, backendResponse.backendId == OFFICIAL_BACKEND_ID,
        )
        val status = if (communityEpoch.isCurrent(
                backendResponse.communityEpoch, backendResponse.receivedAtElapsedMs,
            )) metadata.status else CommunityStatus.UNKNOWN
        val block = status == CommunityStatus.ACTIVE && communityPreferences.blockEnabled.value &&
            !base.shouldBlock && base.resultType == ResultType.PASS_BUT_NOTIFY
        return currentCommunityResult(base.copy(
            communityStatus = status,
            communityBlockCandidate = block,
            shouldBlock = base.shouldBlock || block,
            resultType = if (block) ResultType.INTERCEPT else base.resultType,
            label = if (status == CommunityStatus.ACTIVE && base.label.isBlank())
                backendResponse.response.tag else base.label,
            feedbackPhone = metadata.feedbackPhone,
            feedbackTokenExpiresAt = metadata.feedbackTokenExpiresAt,
            communityEpoch = backendResponse.communityEpoch,
            communityReceivedAtElapsedMs = backendResponse.receivedAtElapsedMs,
        ))
    }

    private suspend fun buildProviderNetworkResult(
        backendResponse: BackendQueryResponse,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val response = backendResponse.response
        val backendId = backendResponse.backendId
        val locationBlackMatch = userListRepository.findBlackListLocationMatch(response.data)
        if (locationBlackMatch != null) {
            val label = locationBlackMatch.remark
                ?: locationRuleLabel(locationBlackMatch.phoneNumber)
            Log.i(TAG, "Black list location hit")
            return CheckResult(
                shouldBlock = true,
                label = label,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = locationBlackMatch.forceBlock,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        val tagBlackMatch = if (response.tag.isNotBlank()) {
            userListRepository.findBlackListTagMatch(response.tag)
        } else {
            null
        }
        if (tagBlackMatch != null) {
            Log.i(TAG, "Black list tag hit")
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.BLACK_LIST,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                forceBlock = tagBlackMatch.forceBlock,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        if (response.isSpam) {
            val tagWhiteMatch = userListRepository.findWhiteListTagMatch(response.tag)
            if (tagWhiteMatch != null) {
                Log.i(TAG, "White list tag hit")
                return CheckResult(
                    shouldBlock = false,
                    label = response.tag,
                    resultType = ResultType.WHITE_LIST,
                    localCost = localCost,
                    networkCost = networkCost,
                    locationInfo = response.data,
                    locationLookupAttempted = true,
                    querySource = response.source,
                    queryBackendId = backendId,
                    feedbackToken = response.feedbackToken,
                )
            }
            return CheckResult(
                shouldBlock = true,
                label = response.tag,
                resultType = ResultType.INTERCEPT,
                localCost = localCost,
                networkCost = networkCost,
                locationInfo = response.data,
                locationLookupAttempted = true,
                querySource = response.source,
                queryBackendId = backendId,
                feedbackToken = response.feedbackToken,
            )
        }

        return CheckResult(
            shouldBlock = false,
            label = response.tag.ifBlank { if (response.source == "360") "暂无标记" else "" },
            resultType = ResultType.PASS_BUT_NOTIFY,
            localCost = localCost,
            networkCost = networkCost,
            locationInfo = response.data,
            locationLookupAttempted = true,
            querySource = response.source,
            queryBackendId = backendId,
            feedbackToken = response.feedbackToken,
        )
    }

    private fun networkTimeoutMs(): Long {
        return prefs.getInt(
            SettingViewModel.KEY_NETWORK_TIMEOUT,
            SettingViewModel.DEFAULT_NETWORK_TIMEOUT_SECONDS
        ).coerceIn(
            SettingViewModel.MIN_NETWORK_TIMEOUT_SECONDS,
            SettingViewModel.MAX_NETWORK_TIMEOUT_SECONDS
        ) * 1000L
    }

    private fun locationRuleLabel(value: String): String {
        return "Location: $value"
    }

    private fun buildNetworkFailureResult(
        exception: Exception,
        localCost: Long,
        networkCost: Long,
    ): CheckResult {
        val resultType = classifyNetworkFailure(exception)
        val apiError = exception as? QueryApiException
        Log.w(
            TAG,
            "Network query failed: category=${networkFailureCategory(resultType)}, " +
                "http=${apiError?.code}, code=${apiError?.errorCode}, " +
                "request_id=${apiError?.requestId}, cost=${networkCost}ms",
        )
        return CheckResult(
            shouldBlock = false,
            label = "",
            resultType = resultType,
            localCost = localCost,
            networkCost = networkCost,
            locationLookupAttempted = true,
        )
    }

    /** 只返回稳定错误分类，禁止把 URL、Token、Header 或响应正文写入日志。 */
    private fun networkFailureCategory(resultType: ResultType): String = when (resultType) {
        ResultType.NETWORK_TIMEOUT -> "timeout"
        ResultType.NETWORK_RATE_LIMITED -> "rate_limited"
        ResultType.NETWORK_SERVER_ERROR -> "server_response"
        ResultType.NETWORK_INVALID_REQUEST -> "invalid_request"
        ResultType.NETWORK_AUTH_ERROR -> "authentication"
        ResultType.NETWORK_INVALID_RESPONSE -> "invalid_response"
        ResultType.NETWORK_UPSTREAM_TIMEOUT -> "upstream_timeout"
        ResultType.NETWORK_UPSTREAM_UNAVAILABLE -> "upstream_unavailable"
        ResultType.NETWORK_FEEDBACK_ERROR -> "feedback_token"
        ResultType.NETWORK_CONNECTION_ERROR -> "network"
        ResultType.QUERY_SERVICE_UNAVAILABLE -> "backend_blocked"
        else -> "unexpected"
    }

    private data class QueryReuseKey(
        val phone: String,
        val backendId: String?,
        val activationId: Long?,
        val timeoutMs: Long,
        val communityEpoch: Long,
    )

    private data class NetworkAttempt(
        val result: Result<BackendQueryResponse>,
        val costMs: Long,
    )

    private data class ReusedNetworkAttempt(
        val attempt: NetworkAttempt,
        val reused: Boolean,
    )

    private data class CachedNetworkAttempt(
        val attempt: NetworkAttempt,
        val completedAtElapsedMs: Long,
    )

    private sealed interface ReuseLookup {
        data class Completed(val attempt: NetworkAttempt) : ReuseLookup
        data class InFlight(
            val deferred: Deferred<NetworkAttempt>,
            val joined: Boolean,
        ) : ReuseLookup
        data class Direct(
            val snapshot: QueryBackendSnapshot?,
            val timeoutMs: Long,
        ) : ReuseLookup
    }
}
