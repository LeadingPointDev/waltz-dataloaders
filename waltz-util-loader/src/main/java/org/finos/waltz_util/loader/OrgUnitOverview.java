package org.finos.waltz_util.loader;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.sql.Timestamp;
import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableOrgUnitOverview.class)
@JsonDeserialize(as = ImmutableOrgUnitOverview.class)
public abstract class OrgUnitOverview {

    @Value.Auxiliary
    @Value.Default
    public Long id(){
        return 0L;
    }


    public abstract String name();


    public abstract Optional<String> description();

    public abstract Optional<Long> parentID();


    @Value.Auxiliary
    public abstract Optional<Timestamp> createdAt();

    @Value.Auxiliary
    @Value.Default
    public Timestamp lastUpdatedAt() {
        return new Timestamp(System.currentTimeMillis());
    }


    public abstract String externalID();


    @Value.Auxiliary
    public abstract Optional<String> parentExternalID();

    @Value.Default
    public String createdBy() {
        return "waltz-loader";
    }

    @Value.Default
    public String lastUpdatedBy() {
        return "waltz-loader";
    }

    @Value.Default
    public String provenance() {
        return "waltz-loader";
    }
}
