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

    @Value.Auxiliary
    public abstract Optional<Long> id();


    public abstract String name();

    public abstract Optional<String> description();

    // asset_code
    public abstract String external_id();


    public abstract Optional<String> parent_external_id();


    public abstract String organisational_unit_external_id();


    @Value.Default
    public ApplicationKind kind() {
        return ApplicationKind.IN_HOUSE;
    }

    @Value.Default
    public String lifecycle_phase() {
        return "ACTIVE";

    }

    @Value.Default
    public String overall_rating() {
        return "Z";
    }


    @Value.Default
    public Criticality criticality() {
        return Criticality.MEDIUM;
    }

    @Value.Default
    public String entity_lifecycle_status() {
        return "ACTIVE";
    }

    public abstract Optional<Timestamp> planned_retirement_date();

    public abstract Optional<Timestamp> actual_retirement_date();

    public abstract Optional<Timestamp> commission_date();

    public abstract Optional<Boolean> isRemoved();


    @Value.Default
    public String provenance() {
        return "waltz-loader";
    }




    public abstract Optional<Long> organisational_unit_id();

}
