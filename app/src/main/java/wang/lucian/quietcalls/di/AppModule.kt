// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.di

import android.content.Context
import androidx.room.Room
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import wang.lucian.quietcalls.data.AppDatabase
import wang.lucian.quietcalls.data.MIGRATION_1_2
import wang.lucian.quietcalls.data.MIGRATION_2_3
import wang.lucian.quietcalls.data.MIGRATION_3_4
import wang.lucian.quietcalls.data.MIGRATION_4_5
import wang.lucian.quietcalls.data.MIGRATION_5_6
import wang.lucian.quietcalls.data.MIGRATION_6_7
import wang.lucian.quietcalls.data.MIGRATION_7_8
import wang.lucian.quietcalls.data.MIGRATION_8_9
import wang.lucian.quietcalls.data.MIGRATION_9_10
import wang.lucian.quietcalls.data.MIGRATION_10_11
import wang.lucian.quietcalls.data.remote.OfficialFeedbackApi
import wang.lucian.quietcalls.data.remote.QueryApi
import wang.lucian.quietcalls.data.remote.SyncApi
import wang.lucian.quietcalls.data.query.QueryBackendProvider
import wang.lucian.quietcalls.data.query.SelfHostedCredentialStore
import wang.lucian.quietcalls.data.query.SelfHostedQueryClientFactory
import wang.lucian.quietcalls.data.preferences.LocalNumberLabelPreferences
import wang.lucian.quietcalls.data.repository.BackupRepository
import wang.lucian.quietcalls.data.repository.BlockedCallRepository
import wang.lucian.quietcalls.data.repository.ContactRepository
import wang.lucian.quietcalls.data.repository.LocalNumberLabelRepository
import wang.lucian.quietcalls.data.repository.QueryRepository
import wang.lucian.quietcalls.data.repository.SelfHostedConfigRepository
import wang.lucian.quietcalls.data.repository.SpamNumberRepository
import wang.lucian.quietcalls.data.repository.SyncRepository
import wang.lucian.quietcalls.data.repository.UserListRepository
import wang.lucian.quietcalls.receiver.CallStateVibrationController
import wang.lucian.quietcalls.service.IncomingCallOverlay
import wang.lucian.quietcalls.smartspacer.SmartspacerInterceptRepository

private const val OFFICIAL_SYNC = "officialSync"
private const val OFFICIAL_QUERY = "officialQuery"
private const val OFFICIAL_FEEDBACK = "officialFeedback"
private const val OFFICIAL_COMMUNITY = "officialCommunity"
private const val OFFICIAL_BASE_URL = "https://pixeltelo.api.mystery0.vip/"

val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "app-database"
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                wang.lucian.quietcalls.data.MIGRATION_11_12,
            )
            .build()
    }

    single { get<AppDatabase>().blockedCallDao() }
    single { get<AppDatabase>().userListDao() }
    single { get<AppDatabase>().localNumberLabelDao() }

    single { BlockedCallRepository() }
    single { ContactRepository(androidContext()) }
    single { UserListRepository(get()) }
    single { LocalNumberLabelRepository(get(), get()) }
    single { BackupRepository(get(), get(), get()) }
    single { SpamNumberRepository() }
    single { SmartspacerInterceptRepository() }
    single { IncomingCallOverlay(androidContext(), get()) }
    single { CallStateVibrationController(androidContext(), get()) }

    single {
        Json { ignoreUnknownKeys = true }
    }

    single<OkHttpClient>(named(OFFICIAL_SYNC)) {
        OkHttpClient.Builder()
            .build()
    }

    single<Retrofit>(named(OFFICIAL_SYNC)) {
        Retrofit.Builder()
            .baseUrl(OFFICIAL_BASE_URL)
            .client(get(named(OFFICIAL_SYNC)))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<SyncApi>(named(OFFICIAL_SYNC)) {
        get<Retrofit>(named(OFFICIAL_SYNC)).create(SyncApi::class.java)
    }

    single<OkHttpClient>(named(OFFICIAL_QUERY)) {
        OkHttpClient.Builder()
            .build()
    }

    single<Retrofit>(named(OFFICIAL_QUERY)) {
        Retrofit.Builder()
            .baseUrl(OFFICIAL_BASE_URL)
            .client(get(named(OFFICIAL_QUERY)))
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single<QueryApi>(named(OFFICIAL_QUERY)) {
        wang.lucian.quietcalls.data.remote.Direct360QueryApi(
            get<OkHttpClient>(named(OFFICIAL_QUERY)).newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )
    }
    single<OfficialFeedbackApi>(named(OFFICIAL_FEEDBACK)) {
        get<Retrofit>(named(OFFICIAL_QUERY)).create(OfficialFeedbackApi::class.java)
    }

    single { wang.lucian.quietcalls.data.query.CommunityQueryEpoch() }
    single { wang.lucian.quietcalls.data.preferences.CommunityReportPreferences(get()) }
    single { wang.lucian.quietcalls.data.repository.CommunityResultInvalidator(androidContext(), get(), get()) }
    single { wang.lucian.quietcalls.data.ReportDeviceIdentityStore(androidContext()) }
    single {
        wang.lucian.quietcalls.data.repository.CommunityReportRepository(
            get(named(OFFICIAL_COMMUNITY)), get(), get(), get(),
        )
    }
    single<wang.lucian.quietcalls.data.remote.OfficialCommunityReportApi>(named(OFFICIAL_COMMUNITY)) {
        val client = get<OkHttpClient>(named(OFFICIAL_QUERY)).newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            // OkHttp 对 503 + Retry-After: 0 的跟进独立于连接重试开关；一次性 body 禁止隐式重发。
            .addInterceptor { chain ->
                val request = chain.request()
                val body = request.body
                val oneShot = body?.let {
                    object : okhttp3.RequestBody() {
                        override fun contentType() = body.contentType()
                        override fun contentLength() = body.contentLength()
                        override fun isOneShot() = true
                        override fun writeTo(sink: okio.BufferedSink) = body.writeTo(sink)
                    }
                }
                chain.proceed(request.newBuilder().method(request.method, oneShot).build())
            }
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        get<Retrofit>(named(OFFICIAL_QUERY)).newBuilder().client(client).build()
            .create(wang.lucian.quietcalls.data.remote.OfficialCommunityReportApi::class.java)
    }

    single { SelfHostedCredentialStore(androidContext()) }
    single { SelfHostedConfigRepository(androidContext(), get(), get()) }
    single { SelfHostedQueryClientFactory(get()) }
    single {
        QueryBackendProvider(
            officialQueryApi = get(named(OFFICIAL_QUERY)),
            configRepository = get(),
            clientFactory = get(),
        )
    }

    single {
        QueryRepository(
            backendProvider = get(),
            officialFeedbackApi = get(named(OFFICIAL_FEEDBACK)),
            preferences = get(),
            communityEpoch = get(),
        )
    }

    single {
        SyncRepository(
            context = androidContext(),
            syncApi = get(named(OFFICIAL_SYNC)),
            okHttpClient = get(named(OFFICIAL_SYNC)),
        )
    }

    single { androidContext().getSharedPreferences("pixel_telo", Context.MODE_PRIVATE) }
    single { LocalNumberLabelPreferences(get()) }
}
