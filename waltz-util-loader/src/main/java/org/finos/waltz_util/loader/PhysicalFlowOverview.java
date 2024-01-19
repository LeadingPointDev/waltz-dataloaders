package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.finos.waltz_util.common.model.Criticality;
import org.immutables.value.Value;

import java.util.Optional;

@Value.Immutable
@JsonSerialize(as = ImmutablePhysicalFlowOverview.class)
@JsonDeserialize(as = ImmutablePhysicalFlowOverview.class)
public abstract class PhysicalFlowOverview {

    public abstract Optional<String> external_id(); // this will be external ID
    public abstract Optional<String> description();

    @JsonIgnore
    public abstract Optional<Long> logical_flow_id();


    @Value.Default
    public String basis_offset(){
        return "T";
    }


    // Logical Flow Fields
    @Value.Default
    public String source_entity_name(){
        return "UNKNOWN";
    }
    public String target_entity_name(){
        return "UNKNOWN";
    }


    public abstract Optional<Character> delimiter();


    //kind: Set by system (figure out)
    @Value.Default
    @Value.Auxiliary
    public String source_entity_kind(){
        return "UNKNOWN";
    }
    @Value.Default
    @Value.Auxiliary
    public String target_entity_kind(){
        return "UNKNOWN";
    }

    @Value.Default
    public String type(){
        return "UNKNOWN";
    }

    @Value.Default
    public String entity_lifecycle_status() {
        return "UNKNOWN";
    }
    @Value.Default
    public String format(){
        return "UNKNOWN";
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








}
