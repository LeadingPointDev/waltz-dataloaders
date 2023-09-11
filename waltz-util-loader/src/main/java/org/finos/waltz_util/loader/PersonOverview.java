package org.finos.waltz_util.loader;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    public abstract Optional<Long> id();


    public abstract String employeeId(); // fixed name, java tends to avoid underscores in class names and method names


    public abstract String displayName();

    public abstract String email();


    public abstract Optional<String> userPrincipalName();


    public abstract Optional<String> departmentName();


    public abstract String kind();

    @Value.Auxiliary
    public abstract Optional<String> managerEmail();


    public abstract Optional<String> managerEmployeeId();


    public abstract Optional<String> title();


    public abstract Optional<String> mobilePhone();


    public abstract Optional<String> officePhone();

    public abstract String organisationalUnitExternalId();

    public abstract Optional<Long> organisationalUnitId();


}
