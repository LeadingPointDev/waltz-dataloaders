package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.immutables.value.Value;

import java.sql.Timestamp;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableApplicationOverview.class)
@JsonDeserialize(as = ImmutableApplicationOverview.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class ApplicationOverview {


    public abstract String name();

    public abstract Optional<String> description();


    public abstract String externalId();


    public abstract Optional<String> parentExternalId();


    public abstract String organisationalUnitExternalId();


    @Value.Default
    public ApplicationKind kind() {
        return ApplicationKind.IN_HOUSE;
    }

    @Value.Default
    public String lifecyclePhase() {
        return "ACTIVE";

    }

    @Value.Default
    public String overallRating() {
        return "Z";
    }


    @Value.Default
    public Criticality criticality() {
        return Criticality.MEDIUM;
    }

    @Value.Default
    public String entityLifecycleStatus() {
        return "ACTIVE";
    }

    public abstract Optional<Timestamp> plannedRetirementDate();

    public abstract Optional<Timestamp> actualRetirementDate();

    public abstract Optional<Timestamp> commissionDate();

    public abstract Boolean isRemoved();


    @Value.Default
    public String provenance() {
        return "waltz-loader";
    }


    @Value.Auxiliary
    public abstract Optional<Long> id();

    public abstract Optional<Long> orgUnitId();

}
