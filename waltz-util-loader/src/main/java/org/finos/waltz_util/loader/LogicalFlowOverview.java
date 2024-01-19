package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutableLogicalFlowOverview.class)
@JsonDeserialize(as = ImmutableLogicalFlowOverview.class)
public abstract class LogicalFlowOverview {


    @Value.Default
    public String source_entity_kind(){
        return "APPLICATION";
    }

    @Value.Default
    public String target_entity_kind(){
        return "APPLICATION";
    }
    //public abstract String source_entity_external_id();
    //public abstract String target_entity_external_id();
    //public abstract String external_id(); // maybe unneccesary



    // logical_flow_decorator Fields
    //public abstract String decorator_kind();
    //public abstract String decorator_id();
    //public abstract String decorator_external_id(); // maybe unneeded


    @JsonIgnore
    public abstract Optional<Long> source_entity_id();
    @JsonIgnore
    public abstract Optional<Long> target_entity_id();

    @JsonIgnore
    public abstract Optional<Long> decorator_id();

    @JsonProperty("TargetDatabase")
    public abstract Optional<String> target_entity_name();

    @JsonProperty("Firm")
    public abstract Optional<String> source_entity_name();

    @JsonProperty("ProcessName")
    public abstract String external_id();










}
