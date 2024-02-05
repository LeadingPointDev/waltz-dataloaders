package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

import java.util.Optional;


@Value.Immutable
@JsonSerialize(as = ImmutablePersonOverview.class)
@JsonDeserialize(as = ImmutablePersonOverview.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class PersonOverview {
    @Value.Auxiliary
    @JsonIgnore
    public abstract Optional<Long> id();


    public abstract String employee_id(); // fixed name, java tends to avoid underscores in class names and method names


    public abstract String display_name();

    public abstract String email();


    public abstract Optional<String> user_principal_name();


    public abstract Optional<String> department_name();


    public abstract String kind();

    @Value.Auxiliary
    public abstract Optional<String> manager_email();


    public abstract Optional<String> manager_employee_id();


    public abstract Optional<String> title();


    public abstract Optional<String> mobile_phone();


    public abstract Optional<String> office_phone();

    public abstract String organisational_unit_external_id();

    @JsonIgnore
    public abstract Optional<Long> organisational_unit_id();


}
