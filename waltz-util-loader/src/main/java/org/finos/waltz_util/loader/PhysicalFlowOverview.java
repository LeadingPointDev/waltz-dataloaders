package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz_util.common.model.Criticality;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutablePhysicalFlowOverview.class)
@JsonDeserialize(as = ImmutablePhysicalFlowOverview.class)
public abstract class PhysicalFlowOverview {

    @JsonProperty("ProcessName")
    public abstract String external_id(); // this will be external ID
    public abstract Optional<String> description();

    @JsonIgnore
    public abstract Optional<Long> logical_flow_id();
    @JsonIgnore
    @Value.Auxiliary
    public abstract Optional<Long> id();


    @Value.Default
    public Integer basis_offset(){
        return 0;
    }


    // Logical Flow Fields
    @Value.Derived
    public String logical_flow_external_id(){
        return external_id() + "-lf";
    }
    @Value.Auxiliary
    public abstract Optional<String> source_entity_name();
    @JsonIgnore
    public abstract Optional<Long> source_entity_id();
    @Value.Auxiliary
    public abstract Optional<String> target_entity_name();
    @JsonIgnore
    public abstract Optional<Long> target_entity_id();
    @Value.Default
    public String source_entity_kind(){
        return "APPLICATION";
    }
    @Value.Default
    public String target_entity_kind(){
        return "APPLICATION";
    }
    // Decorator Table
    @Value.Default
    public String decorator_entity_kind() {
        return "DATA_TYPE";
    }

    @JsonIgnore
    public abstract Optional<Long> logical_flow_decorator_id();

    @Value.Default
    @JsonIgnore
    public Long decorator_entity_id(){
        return 1L;
    }
    @Value.Default
    public String decorator_rating(){
        return "NO_OPINION";
    }
    @JsonIgnore
    @Value.Default
    public Boolean logical_flow_is_removed() {
        return false;
    }

    @Value.Default
    public String type(){
        return "UNKNOWN";
    }
    @Value.Default
    public String entity_lifecycle_status() {
        return "ACTIVE";
    }
    @Value.Default
    public String transport(){
        return "UNKNOWN";
    }
    @Value.Default
    public String frequency(){
        return "UNKNOWN";
    }
    @Value.Default
    public Criticality criticality(){
        return Criticality.UNKNOWN;
    }
    @Value.Default
    public String status() {
        return "UNKNOWN";
    }
    @Value.Default
    public String provenance() {
        return "UNKNOWN";
    }
    @JsonIgnore
    @Value.Default
    public Boolean physical_flow_is_removed() {
        return false;
    }


    // Physical Specification
    @JsonIgnore
    public abstract Optional<Long> physical_specification_id();
    @Value.Derived
    public String physical_specification_external_id(){
        return external_id() + "-spec";
    }

    @Value.Default
    public String owning_entity_kind(){
        return source_entity_kind();
    }
    @Value.Default
    @JsonIgnore
    @Value.Auxiliary
    public Optional<Long> owning_entity_id(){
        return source_entity_id();
    }
    @Value.Default
    public String name(){
        return external_id();
    }
    @Value.Default
    public String format(){
        return "UNKNOWN";
    }








}
