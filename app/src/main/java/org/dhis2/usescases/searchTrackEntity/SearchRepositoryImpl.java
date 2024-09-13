package org.dhis2.usescases.searchTrackEntity;

import android.database.sqlite.SQLiteConstraintException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import org.dhis2.R;
import org.dhis2.bindings.ExtensionsKt;
import org.dhis2.commons.Constants;
import org.dhis2.commons.bindings.ValueExtensionsKt;
import org.dhis2.commons.data.EntryMode;
import org.dhis2.commons.data.EventViewModel;
import org.dhis2.commons.data.EventViewModelType;
import org.dhis2.commons.data.RelationshipDirection;
import org.dhis2.commons.data.RelationshipOwnerType;
import org.dhis2.commons.data.RelationshipViewModel;
import org.dhis2.commons.data.SearchTeiModel;
import org.dhis2.commons.data.tuples.Pair;
import org.dhis2.commons.data.tuples.Trio;
import org.dhis2.commons.filters.FilterManager;
import org.dhis2.commons.filters.data.FilterPresenter;
import org.dhis2.commons.filters.sorting.SortingItem;
import org.dhis2.commons.network.NetworkUtils;
import org.dhis2.commons.reporting.CrashReportController;
import org.dhis2.commons.resources.ResourceManager;
import org.dhis2.data.dhislogic.DhisEnrollmentUtils;
import org.dhis2.data.dhislogic.DhisPeriodUtils;
import org.dhis2.data.forms.dataentry.SearchTEIRepository;
import org.dhis2.data.forms.dataentry.ValueStore;
import org.dhis2.data.forms.dataentry.ValueStoreImpl;
import org.dhis2.data.search.SearchParametersModel;
import org.dhis2.data.sorting.SearchSortingValueSetter;
import org.dhis2.form.model.StoreResult;
import org.dhis2.form.ui.validation.FieldErrorMessageProvider;
import org.dhis2.metadata.usecases.FileResourceConfiguration;
import org.dhis2.metadata.usecases.ProgramConfiguration;
import org.dhis2.metadata.usecases.TrackedEntityInstanceConfiguration;
import org.dhis2.ui.ThemeManager;
import org.dhis2.usescases.teiDownload.TeiDownloader;

import static org.dhis2.usescases.biometrics.BiometricConstantsKt.BIOMETRICS_ENABLED;
import static org.dhis2.commons.biometrics.ExtensionsKt.isBiometricAttribute;
import org.dhis2.utils.DateUtils;
import org.dhis2.utils.ValueUtils;
import org.hisp.dhis.android.core.D2;
import org.hisp.dhis.android.core.arch.call.D2Progress;
import org.hisp.dhis.android.core.arch.helpers.Result;
import org.hisp.dhis.android.core.arch.helpers.UidsHelper;
import org.hisp.dhis.android.core.arch.repositories.scope.RepositoryScope;
import org.hisp.dhis.android.core.common.FeatureType;
import org.hisp.dhis.android.core.common.ObjectStyle;
import org.hisp.dhis.android.core.common.State;
import org.hisp.dhis.android.core.common.ValueType;
import org.hisp.dhis.android.core.enrollment.Enrollment;
import org.hisp.dhis.android.core.enrollment.EnrollmentCreateProjection;
import org.hisp.dhis.android.core.enrollment.EnrollmentStatus;
import org.hisp.dhis.android.core.event.Event;
import org.hisp.dhis.android.core.event.EventCollectionRepository;
import org.hisp.dhis.android.core.event.EventStatus;
import org.hisp.dhis.android.core.maintenance.D2Error;
import org.hisp.dhis.android.core.organisationunit.OrganisationUnit;
import org.hisp.dhis.android.core.period.PeriodType;
import org.hisp.dhis.android.core.program.Program;
import org.hisp.dhis.android.core.program.ProgramStage;
import org.hisp.dhis.android.core.program.ProgramTrackedEntityAttribute;
import org.hisp.dhis.android.core.relationship.Relationship;
import org.hisp.dhis.android.core.relationship.RelationshipItem;
import org.hisp.dhis.android.core.relationship.RelationshipItemTrackedEntityInstance;
import org.hisp.dhis.android.core.relationship.RelationshipType;
import org.hisp.dhis.android.core.settings.AnalyticsDhisVisualizationsGroup;
import org.hisp.dhis.android.core.settings.ProgramConfigurationSetting;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttribute;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityAttributeValue;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstance;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityInstanceCreateProjection;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityType;
import org.hisp.dhis.android.core.trackedentity.TrackedEntityTypeAttribute;
import org.hisp.dhis.android.core.trackedentity.internal.TrackedEntityInstanceDownloader;
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchCollectionRepository;
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchItem;
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchItemAttribute;
import org.hisp.dhis.android.core.trackedentity.search.TrackedEntitySearchItemHelper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dhis2.org.analytics.charts.Charts;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;

public class SearchRepositoryImpl implements SearchRepository {

    private final String teiType;
    private final ResourceManager resources;
    private final D2 d2;
    private final SearchSortingValueSetter sortingValueSetter;
    private TrackedEntitySearchCollectionRepository trackedEntityInstanceQuery;
    private SearchParametersModel savedSearchParameters;
    private FilterManager savedFilters;
    private FilterPresenter filterPresenter;
    private DhisPeriodUtils periodUtils;
    private String currentProgram;
    private final Charts charts;
    private final CrashReportController crashReportController;
    private final NetworkUtils networkUtils;
    private final SearchTEIRepository searchTEIRepository;
    private TrackedEntityInstanceDownloader downloadRepository = null;
    private ThemeManager themeManager;
    private HashSet<String> fetchedTeiUids = new HashSet<>();
    private TeiDownloader teiDownloader;

    public SearchRepositoryImpl(String teiType,
                         @Nullable String initialProgram,
                         D2 d2,
                         FilterPresenter filterPresenter,
                         ResourceManager resources,
                         SearchSortingValueSetter sortingValueSetter,
                         DhisPeriodUtils periodUtils,
                         Charts charts,
                         CrashReportController crashReportController,
                         NetworkUtils networkUtils,
                         SearchTEIRepository searchTEIRepository,
                         ThemeManager themeManager
    ) {
        this.teiType = teiType;
        this.d2 = d2;
        this.resources = resources;
        this.sortingValueSetter = sortingValueSetter;
        this.filterPresenter = filterPresenter;
        this.periodUtils = periodUtils;
        this.charts = charts;
        this.crashReportController = crashReportController;
        this.currentProgram = initialProgram;
        this.networkUtils = networkUtils;
        this.searchTEIRepository = searchTEIRepository;
        this.themeManager = themeManager;
        this.teiDownloader = new TeiDownloader(
                new ProgramConfiguration(d2),
                new TrackedEntityInstanceConfiguration(d2),
                new FileResourceConfiguration(d2),
                currentProgram,
                resources);
    }

    @Override
    public Observable<List<Program>> programsWithRegistration(String programTypeId) {
        return d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE).get()
                .map(UidsHelper::getUidsList)
                .flatMap(orgUnitsUids -> d2.programModule().programs()
                        .byOrganisationUnitList(orgUnitsUids)
                        .byRegistration().isTrue()
                        .byTrackedEntityTypeUid().eq(teiType)
                        .get()).toObservable();
    }

    @Override
    public void clearFetchedList() {
        fetchedTeiUids.clear();
    }

    @NonNull
    @Override
    public LiveData<PagedList<SearchTeiModel>> searchTrackedEntities(SearchParametersModel searchParametersModel, boolean isOnline) {
        boolean allowCache = false;
        if (!searchParametersModel.equals(savedSearchParameters) || !FilterManager.getInstance().sameFilters(savedFilters)) {
            trackedEntityInstanceQuery = getFilteredRepository(searchParametersModel);
        } else {
            getFilteredRepository(searchParametersModel);
            allowCache = true;
        }

        if (!fetchedTeiUids.isEmpty() && searchParametersModel.getSelectedProgram() == null) {
            trackedEntityInstanceQuery = trackedEntityInstanceQuery.excludeUids().in(new ArrayList<>(fetchedTeiUids));
        }

        DataSource<TrackedEntitySearchItem, SearchTeiModel> dataSource;

        if (isOnline && FilterManager.getInstance().getStateFilters().isEmpty()) {
            dataSource = trackedEntityInstanceQuery.allowOnlineCache().eq(allowCache).offlineFirst().getResultDataSource()
                    .map(result -> transformResult(result, searchParametersModel.getSelectedProgram(), false, FilterManager.getInstance().getSortingItem()));
        } else {
            dataSource = trackedEntityInstanceQuery.allowOnlineCache().eq(allowCache).offlineOnly().getResultDataSource()
                    .map(result -> transformResult(result, searchParametersModel.getSelectedProgram(), true, FilterManager.getInstance().getSortingItem()));
        }

        return new LivePagedListBuilder<>(new DataSource.Factory<TrackedEntitySearchItem, SearchTeiModel>() {
            @NonNull
            @Override
            public DataSource<TrackedEntitySearchItem, SearchTeiModel> create() {
                return dataSource;
            }
        }, 10).build();
    }

    @NonNull
    @Override
    public Flowable<List<SearchTeiModel>> searchTeiForMap(SearchParametersModel searchParametersModel, boolean isOnline) {

        boolean allowCache = false;
        if (!searchParametersModel.equals(savedSearchParameters) || !FilterManager.getInstance().equals(savedFilters)) {
            trackedEntityInstanceQuery = getFilteredRepository(searchParametersModel);
        } else {
            allowCache = true;
        }

        if (isOnline && FilterManager.getInstance().getStateFilters().isEmpty())
            return trackedEntityInstanceQuery.allowOnlineCache().eq(allowCache).offlineFirst().get().toFlowable()
                    .flatMapIterable(list -> list)
                    .map(tei -> transform(tei, searchParametersModel.getSelectedProgram(), false, FilterManager.getInstance().getSortingItem()))
                    .toList().toFlowable();
        else
            return trackedEntityInstanceQuery.allowOnlineCache().eq(allowCache).offlineOnly().get().toFlowable()
                    .flatMapIterable(list -> list)
                    .map(tei -> transform(tei, searchParametersModel.getSelectedProgram(), true, FilterManager.getInstance().getSortingItem()))
                    .toList().toFlowable();
    }

    private TrackedEntitySearchCollectionRepository getFilteredRepository(SearchParametersModel searchParametersModel) {
        this.savedSearchParameters = searchParametersModel.copy();
        this.savedFilters = FilterManager.getInstance().copy();

        trackedEntityInstanceQuery = filterPresenter.filteredTrackedEntityInstances(
                searchParametersModel.getSelectedProgram(), teiType
        );

        for (int i = 0; i < searchParametersModel.getQueryData().keySet().size(); i++) {

            String dataId = searchParametersModel.getQueryData().keySet().toArray()[i].toString();
            String dataValue;

            if (searchParametersModel.getUIds().size() == 0) {
                dataValue =searchParametersModel.getQueryData().get(dataId);
            } else {
                dataValue ="%DELETE%";
            }

            boolean isTETypeAttribute = d2.trackedEntityModule().trackedEntityTypeAttributes()
                    .byTrackedEntityTypeUid().eq(teiType)
                    .byTrackedEntityAttributeUid().eq(dataId).one().blockingExists();

            if (searchParametersModel.getSelectedProgram() != null || isTETypeAttribute) {

                boolean isUnique = d2.trackedEntityModule().trackedEntityAttributes().uid(dataId).blockingGet().unique();
                if (isUnique) {
                   if (dataValue.contains(";")) {
                        trackedEntityInstanceQuery = trackedEntityInstanceQuery.byAttribute(dataId).in(dataValue);
                    }else {
                        trackedEntityInstanceQuery = trackedEntityInstanceQuery.byAttribute(dataId).eq(dataValue);
                    }
                } else if (dataValue.contains("_os_")) {
                    dataValue = dataValue.split("_os_")[1];
                    trackedEntityInstanceQuery = trackedEntityInstanceQuery.byAttribute(dataId).eq(dataValue);
                } else if (dataValue.contains(";")) {
                    trackedEntityInstanceQuery = trackedEntityInstanceQuery.byAttribute(dataId).in(dataValue);
                } else
                    trackedEntityInstanceQuery = trackedEntityInstanceQuery.byAttribute(dataId).like(dataValue);
            }
        }

        if (searchParametersModel.getUIds().size() >0){
            trackedEntityInstanceQuery = trackedEntityInstanceQuery.byUIds().in(searchParametersModel.getUIds());
        }

        return trackedEntityInstanceQuery;
    }

    @NonNull
    @Override
    public Observable<Pair<String, String>> saveToEnroll(@NonNull String teiType,
                                                         @NonNull String orgUnit,
                                                         @NonNull String programUid,
                                                         @Nullable String teiUid,
                                                         HashMap<String, String> queryData, Date enrollmentDate,
                                                         @Nullable String fromRelationshipUid) {

        Single<String> enrollmentInitial;
        if (teiUid == null)
            enrollmentInitial = d2.trackedEntityModule().trackedEntityInstances().add(
                    TrackedEntityInstanceCreateProjection.builder()
                            .organisationUnit(orgUnit)
                            .trackedEntityType(teiType)
                            .build()
            );
        else
            enrollmentInitial = Single.just(teiUid);

        return enrollmentInitial.flatMap(uid -> {
                    if (uid == null) {
                        String message = String.format(Locale.US, "Failed to insert new tracked entity " +
                                        "instance for organisationUnit=[%s] and trackedEntity=[%s]",
                                orgUnit, teiType);
                        return Single.error(new SQLiteConstraintException(message));
                    } else {
                        if (fromRelationshipUid != null) {
                            d2.trackedEntityModule().trackedEntityInstanceService().blockingInheritAttributes(fromRelationshipUid, uid, programUid);
                        }
                        ValueStore valueStore = new ValueStoreImpl(d2,
                                uid,
                                EntryMode.ATTR,
                                new DhisEnrollmentUtils(d2),
                                crashReportController,
                                networkUtils,
                                searchTEIRepository,
                                new FieldErrorMessageProvider(resources.getContext()),
                                resources
                        );

                        if (queryData.containsKey(Constants.ENROLLMENT_DATE_UID))
                            queryData.remove(Constants.ENROLLMENT_DATE_UID);
                        for (String key : queryData.keySet()) {
                            String dataValue = queryData.get(key);
                            if (dataValue.contains("_os_"))
                                dataValue = dataValue.split("_os_")[1];

                            boolean isGenerated = d2.trackedEntityModule().trackedEntityAttributes().uid(key).blockingGet().generated();

                            if (!isGenerated) {
                                valueStore.overrideProgram(programUid);
                                StoreResult toreResult = valueStore.save(key, dataValue).blockingFirst();
                            }
                        }
                        return Single.just(uid);
                    }
                }
        ).flatMap(uid ->
                d2.enrollmentModule().enrollments().add(
                                EnrollmentCreateProjection.builder()
                                        .trackedEntityInstance(uid)
                                        .program(programUid)
                                        .organisationUnit(orgUnit)
                                        .build())
                        .map(enrollmentUid -> {
                            boolean displayIncidentDate = d2.programModule().programs().uid(programUid).blockingGet().displayIncidentDate();
                            Date enrollmentDateNoTime = DateUtils.getInstance().getNextPeriod(PeriodType.Daily, enrollmentDate, 0);
                            d2.enrollmentModule().enrollments().uid(enrollmentUid).setEnrollmentDate(enrollmentDateNoTime);
                            if (displayIncidentDate) {
                                d2.enrollmentModule().enrollments().uid(enrollmentUid).setIncidentDate(
                                        DateUtils.getInstance().getToday()
                                );
                            }
                            d2.enrollmentModule().enrollments().uid(enrollmentUid).setFollowUp(false);
                            return Pair.create(enrollmentUid, uid);
                        })
        ).toObservable();
    }

    @Override
    public Observable<List<OrganisationUnit>> getOrgUnits(@Nullable String selectedProgramUid) {

        if (selectedProgramUid != null)
            return d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE)
                    .byProgramUids(Collections.singletonList(selectedProgramUid)).get().toObservable();
        else
            return d2.organisationUnitModule().organisationUnits().byOrganisationUnitScope(OrganisationUnit.Scope.SCOPE_DATA_CAPTURE).get().toObservable();
    }


    private void setEnrollmentInfo(SearchTeiModel searchTei) {
        List<Enrollment> enrollments =
                d2.enrollmentModule().enrollments()
                        .byTrackedEntityInstance().eq(searchTei.getTei().uid())
                        .byDeleted().eq(false)
                        .orderByCreated(RepositoryScope.OrderByDirection.DESC)
                        .blockingGet();
        for (Enrollment enrollment : enrollments) {
            if (enrollments.indexOf(enrollment) == 0)
                searchTei.resetEnrollments();
            searchTei.addEnrollment(enrollment);
            Program program = d2.programModule().programs().byUid().eq(enrollment.program()).one().blockingGet();
            if (program.displayFrontPageList()) {
                searchTei.addProgramInfo(program);
            }
            searchTei.addEnrollmentInfo(getProgramInfo(program));
        }
    }

    private Trio<String, String, String> getProgramInfo(Program program) {
        String programColor = program.style() != null && program.style().color() != null ? program.style().color() : "";
        String programIcon = program.style() != null && program.style().icon() != null ? program.style().icon() : "";
        return Trio.create(program.displayName(), programColor, programIcon);
    }

    private void setAttributesInfo(SearchTeiModel searchTei, TrackedEntitySearchItem searchTeiItem) {
        for (TrackedEntitySearchItemAttribute attribute : searchTeiItem.getAttributeValues()) {
            if (attribute.getDisplayInList() && isAcceptedValueType(attribute.getValueType())) {
                setAttributeValue(searchTei, attribute);
            }
        }
    }

    private boolean isAcceptedValueType(ValueType valueType) {
        return switch (valueType) {
            case IMAGE, COORDINATE, FILE_RESOURCE -> false;
            default -> true;
        };
    }

    private void setAttributeValue(SearchTeiModel searchTei, TrackedEntitySearchItemAttribute attribute) {
        String value = attribute.getValue();
        String transformedValue;
        if (value != null) {
            transformedValue = ValueUtils.transformValue(d2, value, attribute.getValueType(), attribute.getOptionSet());
        } else {
            transformedValue = sortingValueSetter.getUnknownLabel();
        }
        TrackedEntityAttributeValue attributeValue = TrackedEntityAttributeValue.builder()
                .created(attribute.getCreated())
                .lastUpdated(attribute.getLastUpdated())
                .trackedEntityAttribute(attribute.getAttribute())
                .trackedEntityInstance(searchTei.getTei().uid())
                .value(transformedValue)
                .build();

        searchTei.addAttributeValue(attribute.getDisplayFormName(), attributeValue);

        if (attribute.getValueType() == ValueType.TEXT || attribute.getValueType() == ValueType.LONG_TEXT) {
            searchTei.addTextAttribute(attribute.getDisplayName(), attributeValue);
        }
    }

    private void setOverdueEvents(@NonNull SearchTeiModel tei, Program selectedProgram) {
        String teiId = tei.getTei() != null && tei.getTei().uid() != null ? tei.getTei().uid() : "";
        List<Enrollment> enrollments = d2.enrollmentModule().enrollments().byTrackedEntityInstance().eq(teiId).blockingGet();

        EventCollectionRepository scheduledEvents = d2.eventModule().events().byEnrollmentUid().in(UidsHelper.getUidsList(enrollments))
                .byStatus().eq(EventStatus.SCHEDULE)
                .byDueDate().beforeOrEqual(new Date());

        EventCollectionRepository overdueEvents = d2.eventModule().events().byEnrollmentUid().in(UidsHelper.getUidsList(enrollments)).byStatus().eq(EventStatus.OVERDUE);

        if (selectedProgram != null) {
            scheduledEvents = scheduledEvents.byProgramUid().eq(selectedProgram.uid()).orderByDueDate(RepositoryScope.OrderByDirection.DESC);
            overdueEvents = overdueEvents.byProgramUid().eq(selectedProgram.uid()).orderByDueDate(RepositoryScope.OrderByDirection.DESC);
        }

        int count;
        List<Event> scheduleList = scheduledEvents.blockingGet();
        List<Event> overdueList = overdueEvents.blockingGet();
        count = overdueList.size() + scheduleList.size();

        if (count > 0) {
            tei.setHasOverdue(true);
            Date scheduleDate = scheduleList.size() > 0 ? scheduleList.get(0).dueDate() : null;
            Date overdueDate = overdueList.size() > 0 ? overdueList.get(0).dueDate() : null;
            Date dateToShow = null;
            if (scheduleDate != null && overdueDate != null) {
                if (scheduleDate.before(overdueDate)) {
                    dateToShow = overdueDate;
                } else {
                    dateToShow = scheduleDate;
                }
            } else if (scheduleDate != null) {
                dateToShow = scheduleDate;
            } else if (overdueDate != null) {
                dateToShow = overdueDate;
            }
            tei.setOverdueDate(dateToShow);
        }
    }

    private void setRelationshipsInfo(@NonNull SearchTeiModel searchTeiModel, Program selectedProgram) {
        List<RelationshipViewModel> relationshipViewModels = new ArrayList<>();
        List<Relationship> relationships = d2.relationshipModule().relationships().getByItem(
                RelationshipItem.builder().trackedEntityInstance(
                        RelationshipItemTrackedEntityInstance.builder()
                                .trackedEntityInstance(searchTeiModel.getTei().uid())
                                .build()
                ).build()
        );
        for (Relationship relationship : relationships) {
            if (relationship.from().trackedEntityInstance() != null) {
                RelationshipType relationshipType =
                        d2.relationshipModule().relationshipTypes().uid(relationship.relationshipType()).blockingGet();

                String relationshipTEIUid;
                RelationshipDirection direction;
                if (!searchTeiModel.getTei().uid().equals(relationship.from().trackedEntityInstance().trackedEntityInstance())) {
                    relationshipTEIUid = relationship.from().trackedEntityInstance().trackedEntityInstance();
                    direction = RelationshipDirection.FROM;
                } else {
                    relationshipTEIUid = relationship.to().trackedEntityInstance().trackedEntityInstance();
                    direction = RelationshipDirection.TO;
                }

                String fromTeiUid = relationship.from().trackedEntityInstance().trackedEntityInstance();
                String toTeiUid = relationship.to().trackedEntityInstance().trackedEntityInstance();

                TrackedEntityInstance fromTei = d2.trackedEntityModule().trackedEntityInstances().uid(fromTeiUid).blockingGet();
                TrackedEntityInstance toTei = d2.trackedEntityModule().trackedEntityInstances().uid(toTeiUid).blockingGet();

                List<kotlin.Pair<String, String>> fromValues = new ArrayList<>();
                List<TrackedEntityAttributeValue> fromAttr = getTrackedEntityAttributesForRelationship(fromTei, selectedProgram);
                List<kotlin.Pair<String, String>> toValues = new ArrayList<>();
                List<TrackedEntityAttributeValue> toAttr = getTrackedEntityAttributesForRelationship(toTei, selectedProgram);
                for (TrackedEntityAttributeValue attributeValue : fromAttr) {
                    fromValues.add(new kotlin.Pair<>(attributeValue.trackedEntityAttribute(), attributeValue.value()));
                }
                for (TrackedEntityAttributeValue attributeValue : toAttr) {
                    toValues.add(new kotlin.Pair<>(attributeValue.trackedEntityAttribute(), attributeValue.value()));
                }
                relationshipViewModels.add(new RelationshipViewModel(
                        relationship,
                        fromTei.geometry(),
                        toTei.geometry(),
                        relationshipType,
                        direction,
                        relationshipTEIUid,
                        RelationshipOwnerType.TEI,
                        fromValues,
                        toValues,
                        ExtensionsKt.profilePicturePath(fromTei, d2, selectedProgram.uid()),
                        ExtensionsKt.profilePicturePath(toTei, d2, selectedProgram.uid()),
                        getTeiDefaultRes(fromTei),
                        getTeiDefaultRes(toTei),
                        -1,
                        true
                ));
            }
        }

        searchTeiModel.setRelationships(relationshipViewModels);
    }

    private int getTeiDefaultRes(TrackedEntityInstance tei) {
        TrackedEntityType teiType = d2.trackedEntityModule().trackedEntityTypes().uid(tei.trackedEntityType()).blockingGet();
        return resources.getObjectStyleDrawableResource(teiType.style().icon(), R.drawable.photo_temp_gray);
    }

    private List<TrackedEntityAttributeValue> getTrackedEntityAttributesForRelationship(TrackedEntityInstance tei, Program selectedProgram) {

        List<TrackedEntityAttributeValue> values;
        List<String> attributeUids = new ArrayList<>();
        List<ProgramTrackedEntityAttribute> programTrackedEntityAttributes = d2.programModule().programTrackedEntityAttributes()
                .byProgram().eq(selectedProgram.uid())
                .byDisplayInList().isTrue()
                .orderBySortOrder(RepositoryScope.OrderByDirection.ASC)
                .blockingGet();
        for (ProgramTrackedEntityAttribute programAttribute : programTrackedEntityAttributes) {
            attributeUids.add(programAttribute.trackedEntityAttribute().uid());
        }
        values = d2.trackedEntityModule().trackedEntityAttributeValues()
                .byTrackedEntityInstance().eq(tei.uid())
                .byTrackedEntityAttribute().in(attributeUids).blockingGet();

        if (values.isEmpty()) {
            attributeUids.clear();
            List<TrackedEntityTypeAttribute> typeAttributes = d2.trackedEntityModule().trackedEntityTypeAttributes()
                    .byTrackedEntityTypeUid().eq(tei.trackedEntityType())
                    .byDisplayInList().isTrue()
                    .blockingGet();

            for (TrackedEntityTypeAttribute typeAttribute : typeAttributes) {
                attributeUids.add(typeAttribute.trackedEntityAttribute().uid());
            }
            values = d2.trackedEntityModule().trackedEntityAttributeValues()
                    .byTrackedEntityInstance().eq(tei.uid())
                    .byTrackedEntityAttribute().in(attributeUids).blockingGet();
        }

        return values;
    }

    @Override
    public String getProgramColor(@NonNull String programUid) {
        Program program = d2.programModule().programs().byUid().eq(programUid).one().blockingGet();
        return program.style() != null ?
                program.style().color() != null ?
                        program.style().color() :
                        "" :
                "";
    }

    @Override
    public void setCurrentTheme(@Nullable ProgramSpinnerModel selectedProgram) {
        if (selectedProgram != null) {
            themeManager.setProgramTheme(selectedProgram.getUid());
        } else {
            themeManager.setTrackedEntityTypeTheme(teiType);
        }
    }

    @Nullable
    @Override
    public List<String> trackedEntityTypeFields() {
        List<ProgramTrackedEntityAttribute> programTrackedEntityAttributes =
                d2.programModule().programTrackedEntityAttributes()
                        .byProgram().eq(currentProgram)
                        .bySearchable().isTrue()
                        .blockingGet();

        List<String> attrNames = new ArrayList<>();
        for (ProgramTrackedEntityAttribute searchAttribute : programTrackedEntityAttributes) {
            String attrUid = searchAttribute.trackedEntityAttribute().uid();
            boolean isTrackedEntityTypeAttribute = !d2.trackedEntityModule().trackedEntityTypeAttributes()
                    .byTrackedEntityTypeUid().eq(teiType)
                    .byTrackedEntityAttributeUid().eq(attrUid)
                    .blockingIsEmpty();
            if (isTrackedEntityTypeAttribute) {
                TrackedEntityAttribute attr = d2.trackedEntityModule().trackedEntityAttributes()
                        .uid(attrUid)
                        .blockingGet();
                attrNames.add(attr.displayFormName());
            }
        }
        return attrNames;
    }

    @Override
    public boolean filtersApplyOnGlobalSearch() {
        return FilterManager.getInstance().getTotalFilters() == 0 ||
                !FilterManager.getInstance().getOrgUnitFilters().isEmpty() ||
                !FilterManager.getInstance().getStateFilters().isEmpty();
    }

    @Override
    public Observable<TrackedEntityType> getTrackedEntityType(String trackedEntityUid) {
        return d2.trackedEntityModule().trackedEntityTypes().uid(trackedEntityUid).get().toObservable();
    }

    @Override
    public TrackedEntityType getTrackedEntityType() {
        return d2.trackedEntityModule().trackedEntityTypes().uid(teiType).blockingGet();
    }

    @Override
    public List<EventViewModel> getEventsForMap(List<SearchTeiModel> teis) {
        List<EventViewModel> eventViewModels = new ArrayList<>();
        List<String> teiUidList = new ArrayList<>();
        for (SearchTeiModel tei : teis) {
            teiUidList.add(tei.getTei().uid());
        }

        List<Event> events = d2.eventModule().events()
                .byTrackedEntityInstanceUids(teiUidList)
                .byDeleted().isFalse()
                .blockingGet();

        for (Event event : events) {
            ProgramStage stage = d2.programModule().programStages()
                    .uid(event.programStage())
                    .blockingGet();

            OrganisationUnit organisationUnit = d2.organisationUnitModule()
                    .organisationUnits()
                    .uid(event.organisationUnit())
                    .blockingGet();

            eventViewModels.add(
                    new EventViewModel(
                            EventViewModelType.EVENT,
                            stage,
                            event,
                            0,
                            null,
                            true,
                            true,
                            organisationUnit.displayName(),
                            null,
                            null,
                            false,
                            false,
                            false,
                            false,
                            periodUtils.getPeriodUIString(stage.periodType(), event.eventDate() != null ? event.eventDate() : event.dueDate(), Locale.getDefault()),
                            null
                    ));
        }

        return eventViewModels;
    }

    @Override
    public SearchTeiModel getTrackedEntityInfo(String teiUid, Program selectedProgram, SortingItem sortingItem) {
        return transform(
                d2.trackedEntityModule().trackedEntitySearch().uid(teiUid).blockingGet(),
                selectedProgram,
                true,
                sortingItem
        );

    }

    @Override
    public EventViewModel getEventInfo(String uid) {
        Event event = d2.eventModule().events().uid(uid).blockingGet();

        ProgramStage stage = d2.programModule().programStages()
                .uid(event.programStage())
                .blockingGet();

        OrganisationUnit organisationUnit = d2.organisationUnitModule()
                .organisationUnits()
                .uid(event.organisationUnit())
                .blockingGet();

        return new EventViewModel(EventViewModelType.EVENT,
                stage,
                event,
                0,
                null,
                true,
                true,
                organisationUnit.displayName(),
                null,
                null,
                false,
                false,
                false,
                false,
                periodUtils.getPeriodUIString(stage.periodType(), event.eventDate() != null ? event.eventDate() : event.dueDate(), Locale.getDefault()),
                null
        );
    }

    @Override
    public Observable<D2Progress> downloadTei(String teiUid) {
        downloadRepository = d2.trackedEntityModule().trackedEntityInstanceDownloader()
                .byUid().eq(teiUid)
                .byProgramUid(currentProgram);
        return Observable.merge(
                downloadRepository
                        .overwrite(true)
                        .download(),
                d2.fileResourceModule().fileResourceDownloader().download()
        );
    }

    @Override
    public TeiDownloadResult download(String teiUid, @Nullable String enrollmentUid, @Nullable String reason) {
        return teiDownloader.download(teiUid, enrollmentUid, reason);
    }

    private SearchTeiModel transformResult(Result<TrackedEntitySearchItem, D2Error> result, @Nullable Program selectedProgram, boolean offlineOnly, SortingItem sortingItem) {
        try {
            return transform(result.getOrThrow(), selectedProgram, offlineOnly, sortingItem);
        } catch (Exception e) {
            SearchTeiModel errorModel = new SearchTeiModel();
            errorModel.onlineErrorMessage = resources.parseD2Error(e);
            errorModel.onlineErrorCode = ((D2Error) e).errorCode();
            return errorModel;
        }
    }

    private SearchTeiModel transform(TrackedEntitySearchItem searchItem, @Nullable Program selectedProgram, boolean offlineOnly, SortingItem sortingItem) {
        if (!fetchedTeiUids.contains(searchItem.uid())) {
            fetchedTeiUids.add(searchItem.uid());
        }
        TrackedEntityInstance teiFromItem = TrackedEntitySearchItemHelper.INSTANCE.toTrackedEntityInstance(searchItem);

        TrackedEntityInstance dbTei = searchItem.isOnline() ?
                d2.trackedEntityModule().trackedEntityInstances().uid(searchItem.uid()).blockingGet() :
                teiFromItem;

        SearchTeiModel searchTei = new SearchTeiModel();
        if (dbTei != null && dbTei.aggregatedSyncState() != State.RELATIONSHIP) {
            searchTei.setTei(dbTei);
            if (selectedProgram != null && d2.enrollmentModule().enrollments().byTrackedEntityInstance().eq(dbTei.uid()).byProgram().eq(selectedProgram.uid()).one().blockingExists()) {
                List<Enrollment> possibleEnrollments = d2.enrollmentModule().enrollments()
                        .byTrackedEntityInstance().eq(dbTei.uid())
                        .byProgram().eq(selectedProgram.uid())
                        .orderByEnrollmentDate(RepositoryScope.OrderByDirection.DESC)
                        .blockingGet();
                for (Enrollment enrollment : possibleEnrollments) {
                    if (enrollment.status() == EnrollmentStatus.ACTIVE) {
                        searchTei.setCurrentEnrollment(enrollment);
                        break;
                    }
                }
                if (searchTei.getSelectedEnrollment() == null) {
                    searchTei.setCurrentEnrollment(possibleEnrollments.get(0));
                }
            }

            searchTei.setOnline(!searchItem.isOnline());

            if (offlineOnly)
                searchTei.setOnline(!offlineOnly);

            if (dbTei.deleted() != null && dbTei.deleted()) {
                searchTei.setOnline(true);
            }

            setEnrollmentInfo(searchTei);
            setAttributesInfo(searchTei, searchItem);
            setOverdueEvents(searchTei, selectedProgram);
            if (selectedProgram != null) {
                setRelationshipsInfo(searchTei, selectedProgram);
            }
            if (searchTei.getSelectedEnrollment() != null) {
                searchTei.setEnrolledOrgUnit(d2.organisationUnitModule().organisationUnits().uid(searchTei.getSelectedEnrollment().organisationUnit()).blockingGet().name());
            } else {
                searchTei.setEnrolledOrgUnit(d2.organisationUnitModule().organisationUnits().uid(searchTei.getTei().organisationUnit()).blockingGet().name());
            }
            searchTei.setProfilePicture(profilePicturePath(dbTei, selectedProgram));
        } else {
            searchTei.setTei(teiFromItem);
            searchTei.setEnrolledOrgUnit(d2.organisationUnitModule().organisationUnits().uid(searchTei.getTei().organisationUnit()).blockingGet().name());

            for (TrackedEntitySearchItemAttribute attribute : searchItem.getAttributeValues()) {
                if (attribute.getDisplayInList()) {
                    addAttribute(searchTei, attribute);
                }
            }
        }

        ObjectStyle os = searchItem.getType().style();
        searchTei.setDefaultTypeIcon(os != null ? os.icon() : null);

        searchTei.setHeader(searchItem.getHeader());
        searchTei.setSortingValue(sortingValueSetter.setSortingItem(searchTei, sortingItem));
        searchTei.setTEType(searchItem.getType().displayName());
        return searchTei;
    }

    private void addAttribute(SearchTeiModel searchTei, TrackedEntitySearchItemAttribute att) {
        TrackedEntityAttributeValue attributeValue = TrackedEntityAttributeValue.builder()
                .value(att.getValue())
                .created(att.getCreated())
                .lastUpdated(att.getLastUpdated())
                .trackedEntityAttribute(att.getAttribute())
                .trackedEntityInstance(searchTei.getTei().uid())
                .build();

        String friendlyValue = ValueExtensionsKt.userFriendlyValue(attributeValue, d2);

        TrackedEntityAttributeValue friendlyAttributeValue = attributeValue.toBuilder()
                .value(friendlyValue)
                .build();

        if (att.getValueType() == ValueType.IMAGE)
            searchTei.setProfilePicture(att.getAttribute());

        searchTei.addAttributeValue(att.getDisplayFormName(), friendlyAttributeValue);
    }

    private String profilePicturePath(TrackedEntityInstance tei, @Nullable Program selectedProgram) {
        return ExtensionsKt.profilePicturePath(tei, d2, selectedProgram != null ? selectedProgram.uid() : null);
    }

    @Override
    public void setCurrentProgram(String currentProgram) {
        this.currentProgram = currentProgram;
    }

    @Override
    public String currentProgram() {
        return currentProgram;
    }

    @Override
    public List<AnalyticsDhisVisualizationsGroup> getProgramVisualizationGroups(String programUid) {
        if (charts != null) {
            return charts.getVisualizationGroups(programUid);
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Observable<Boolean> programHasBiometrics() {
        String biometricAttributeUid = getBiometricAttributeUid();

        if (biometricAttributeUid != null && BIOMETRICS_ENABLED){
            return Observable.just(true);
        } else {
            return  Observable.just(false);
        }
    }

    @Override
    public String getBiometricAttributeUid() {
        String programUid = currentProgram();

        if (programUid != null) {
            List<ProgramTrackedEntityAttribute> attributeList =
                    d2.programModule().programTrackedEntityAttributes().byProgram().eq(
                            programUid).blockingGet();

            for (ProgramTrackedEntityAttribute attribute : attributeList) {
                if (isBiometricAttribute(attribute) && BIOMETRICS_ENABLED) {
                    return attribute.trackedEntityAttribute().uid();
                }
            }

        }

        return  null;
    }

    @Override
    public boolean programStagesHaveCoordinates(String programUid) {
        return !d2.programModule().programStages()
                .byProgramUid().eq(programUid)
                .byFeatureType().notIn(FeatureType.NONE)
                .blockingIsEmpty();
    }

    @Override
    public boolean teTypeAttributesHaveCoordinates(String typeId) {
        List<TrackedEntityTypeAttribute> teAttributes = d2.trackedEntityModule().trackedEntityTypeAttributes()
                .byTrackedEntityTypeUid().eq(typeId)
                .blockingGet();
        List<String> teAttributeUids = new ArrayList<>();
        for (TrackedEntityTypeAttribute teTypeAttr : teAttributes) {
            teAttributeUids.add(teTypeAttr.trackedEntityAttribute().uid());
        }

        return !d2.trackedEntityModule().trackedEntityAttributes()
                .byUid().in(teAttributeUids)
                .byValueType().in(ValueType.COORDINATE, ValueType.GEOJSON)
                .blockingIsEmpty();
    }

    @Override
    public boolean programAttributesHaveCoordinates(String programUid) {
        List<ProgramTrackedEntityAttribute> programAttributes = d2.programModule().programTrackedEntityAttributes()
                .byProgram().eq(programUid)
                .blockingGet();
        List<String> programAttributeUids = new ArrayList<>();
        for (ProgramTrackedEntityAttribute programAttr : programAttributes) {
            programAttributeUids.add(programAttr.trackedEntityAttribute().uid());
        }

        return !d2.trackedEntityModule().trackedEntityAttributes()
                .byUid().in(programAttributeUids)
                .byValueType().in(ValueType.COORDINATE, ValueType.GEOJSON)
                .blockingIsEmpty();
    }

    @Override
    public boolean eventsHaveCoordinates(String programUid) {
        return !d2.eventModule().events()
                .byDeleted().isFalse()
                .byProgramUid().eq(programUid)
                .byGeometryCoordinates().isNotNull()
                .blockingIsEmpty();
    }

    @Nullable
    @Override
    public Program getProgram(@Nullable String programUid) {
        if (programUid == null)
            return null;
        return d2.programModule().programs().uid(programUid).blockingGet();
    }

    @Override
    public @NotNull Map<String, String> filterQueryForProgram(@NotNull Map<String, String> queryData, @Nullable String programUid) {
        Map<String, String> filteredQuery = new HashMap<>();
        for (Map.Entry<String, String> entry : queryData.entrySet()) {
            String attributeUid = entry.getKey();
            String value = entry.getValue();
            if (programUid == null && attributeIsForType(attributeUid) ||
                    programUid != null && attributeBelongsToProgram(attributeUid, programUid)
            ) {
                filteredQuery.put(attributeUid, value);
            }
        }
        return filteredQuery;
    }


    private boolean attributeIsForType(String attributeUid) {
        return !d2.trackedEntityModule().trackedEntityTypeAttributes()
                .byTrackedEntityTypeUid().eq(teiType)
                .byTrackedEntityAttributeUid().eq(attributeUid)
                .blockingIsEmpty();
    }

    private boolean attributeBelongsToProgram(String attributeUid, String programUid) {
        return !d2.programModule().programTrackedEntityAttributes()
                .byProgram().eq(programUid)
                .byTrackedEntityAttribute().eq(attributeUid)
                .bySearchable().isTrue()
                .blockingIsEmpty();
    }

    @Override
    public boolean canCreateInProgramWithoutSearch() {
        if (currentProgram == null) {
            return false;
        } else {
            ProgramConfigurationSetting programConfiguration = d2.settingModule().appearanceSettings().getProgramConfigurationByUid(currentProgram);
            return programConfiguration != null && Boolean.TRUE.equals(programConfiguration.optionalSearch());
        }
    }
}
