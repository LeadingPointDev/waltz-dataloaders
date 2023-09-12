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

    public abstract Optional<Long> parent_id();


    @Value.Auxiliary
    public abstract Optional<Timestamp> created_at();

    @Value.Auxiliary
    @Value.Default
    public Timestamp last_updated_at() {
        return new Timestamp(System.currentTimeMillis());
    }


    public abstract String external_id();


    @Value.Auxiliary
    public abstract Optional<String> parent_external_id();

    @Value.Default
    public String created_by() {
        return "waltz-loader";
    }

    @Value.Default
    public String last_updated_by() {
        return "waltz-loader";
    }

    @Value.Default
    public String provenance() {
        return "waltz-loader";
    }
}
