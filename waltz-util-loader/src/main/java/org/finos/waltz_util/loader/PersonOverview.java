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

    @JsonProperty("resourceId")
    public abstract String employeeId(); // fixed name, java tends to avoid underscores in class names and method names

    @JsonProperty("name")
    public abstract String displayName();

    public abstract String email();


    @JsonProperty("username")
    public abstract Optional<String> userPrincipalName();

    @JsonProperty("departmentName")
    public abstract Optional<String> departmentName();

    @JsonProperty("personKind")
    public abstract String kind();

    @Value.Auxiliary
    @JsonProperty("manager_email")
    public abstract Optional<String> managerEmail();

    @JsonProperty("managerResourceId")
    public abstract Optional<String> managerEmployeeId();

    @JsonProperty("title")
    public abstract Optional<String> title();

    @JsonProperty("mobilePhone")
    public abstract Optional<String> mobilePhone();

    @JsonProperty("officePhone")
    public abstract Optional<String> officePhone();

    @JsonProperty("departmentId")
    public abstract String organisationalUnitExternalId();

    public abstract Optional<Long> organisationalUnitId();


}
