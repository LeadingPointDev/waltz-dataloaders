package org.finos.waltz_util.loader;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableDataTypeOverview.class)
@JsonDeserialize(as = ImmutableDataTypeOverview.class)
public abstract class DataTypeOverview {

    @Value.Auxiliary
    @JsonIgnore
    public abstract Long id();

    public abstract String code();

    public abstract String name();

    public abstract Optional<String> description();


    public abstract Optional<Long> parent_id();

    @Value.Default
    public Boolean concrete(){
        return true;
    }

    @Value.Default
    public Boolean unknown(){
        return false;
    }

    @Value.Default
    public Boolean depreciated(){
        return false;
    }


}
