package org.dhis2.usescases.teiDashboard.dashboardsfragments.feedback

import android.content.Context
import dagger.Module
import dagger.Provides
import org.dhis2.data.dagger.PerFragment
import org.dhis2.data.dhislogic.DhisEventUtils
import org.dhis2.data.dhislogic.DhisPeriodUtils
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.TeiDataRepository
import org.dhis2.usescases.teiDashboard.dashboardfragments.teidata.TeiDataRepositoryImpl
import org.dhis2.usescases.teiDashboard.dashboardsfragments.enrollment.EnrollmentInfoD2Repository
import org.dhis2.usescases.teiDashboard.dashboardsfragments.enrollment.EnrollmentInfoRepository
import org.dhis2.usescases.teiDashboard.dashboardsfragments.enrollment.GetEnrollmentInfo
import org.dhis2.usescases.teiDashboard.dashboardsfragments.systemInfo.GetSystemInfo
import org.dhis2.usescases.teiDashboard.dashboardsfragments.systemInfo.SystemInfoD2Repository
import org.dhis2.usescases.teiDashboard.dashboardsfragments.systemInfo.SystemInfoRepository
import org.hisp.dhis.android.core.D2

@PerFragment
@Module
class FeedbackModule(
    private val programUid: String,
    private val teiUid: String,
    private val enrollmentUid: String,
    private val context: Context
) {
    @Provides
    @PerFragment
    fun provideFeedbackPresenter(
        feedbackProgramRepository: FeedbackProgramRepository
    ): FeedbackPresenter {
        return FeedbackPresenter(feedbackProgramRepository)
    }

    @Provides
    @PerFragment
    fun provideFeedbackContentPresenter(
        getFeedback: GetFeedback,
        getSystemInfo: GetSystemInfo,
        getEnrollmentInfo: GetEnrollmentInfo
    ): FeedbackContentPresenter {
        return FeedbackContentPresenter(getFeedback, getSystemInfo, getEnrollmentInfo)
    }

    @Provides
    @PerFragment
    fun provideGetFeedback(
        teiDataRepository: TeiDataRepository,
        valuesRepository: ValuesRepository,
        dataElementRepository: DataElementRepository
    ): GetFeedback {
        return GetFeedback(teiDataRepository, dataElementRepository, valuesRepository)
    }

    @Provides
    @PerFragment
    fun provideGetSystemInfo(systemInfoRepository: SystemInfoRepository): GetSystemInfo {
        return GetSystemInfo(systemInfoRepository)
    }

    @Provides
    @PerFragment
    fun provideGetEnrollmentInfo(enrollmentInfoRepository: EnrollmentInfoRepository): GetEnrollmentInfo {
        return GetEnrollmentInfo(enrollmentInfoRepository)
    }

    @Provides
    @PerFragment
    fun providesFeedbackProgramRepository(d2: D2): FeedbackProgramRepository {
        return D2FeedbackProgramRepository(d2)
    }

    @Provides
    @PerFragment
    fun provideTeiDataRepository(d2: D2, dhisPeriodUtils: DhisPeriodUtils): TeiDataRepository {
        return TeiDataRepositoryImpl(d2, programUid, teiUid, enrollmentUid, dhisPeriodUtils)
    }

    @Provides
    @PerFragment
    fun provideValuesRepository(d2: D2): ValuesRepository {
        return ValuesD2Repository(d2, context)
    }

    @Provides
    @PerFragment
    fun provideDataElementRepository(d2: D2): DataElementRepository {
        return DataElementD2Repository(d2)
    }

    @Provides
    @PerFragment
    fun provideSystemInfoRepository(d2: D2): SystemInfoRepository {
        return SystemInfoD2Repository(d2)
    }

    @Provides
    @PerFragment
    fun provideEnrollmentInfoRepository(d2: D2): EnrollmentInfoRepository {
        return EnrollmentInfoD2Repository(d2)
    }
}
