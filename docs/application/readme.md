# Application Data Loader

![Application Loader Diagram](<src/Application Data Loader with UI.png>)

# Mapping Details

It can be difficult to see the exact mappings in the above diagram. See dbml...

## Mapping to Waltz Database (Application Table)

```
Ref: "waltzloader"."application-out.json"."name" > "waltz"."application"."name"
Ref: "waltzloader"."application-out.json"."description" > "waltz"."application"."description"
Ref: "waltzloader"."application-out.json"."asset_code" > "waltz"."application"."asset_code"
Ref: "waltzloader"."application-out.json"."organisational_unit_external_id" > "waltz"."organisational_unit"."external_id"
Ref: "waltzloader"."application-out.json"."kind" > "waltz"."application"."kind"
Ref: "waltzloader"."application-out.json"."lifecycle_phase" > "waltz"."application"."lifecycle_phase"
Ref: "waltzloader"."application-out.json"."parent_asset_code" > "waltz"."application"."parent_asset_code"
Ref: "waltzloader"."application-out.json"."overall_rating" > "waltz"."application"."overall_rating"
Ref: "waltzloader"."application-out.json"."provenance" > "waltz"."application"."provenance"
Ref: "waltzloader"."application-out.json"."business_criticality" > "waltz"."application"."business_criticality"
Ref: "waltzloader"."application-out.json"."entity_lifecycle_status" > "waltz"."application"."entity_lifecycle_status"
Ref: "waltzloader"."application-out.json"."planned_retirement_date" > "waltz"."application"."planned_retirement_date"
Ref: "waltzloader"."application-out.json"."actual_retirement_date" > "waltz"."application"."actual_retirement_date"
Ref: "waltzloader"."application-out.json"."commission_date" > "waltz"."application"."commission_date"
```

## Mapping to Waltz UI (Application Summary)

```
Ref: "waltz"."application"."name" > "waltzUI"."Application Summary"."Name"
Ref: "waltz"."application"."asset_code" > "waltzUI"."Application Summary"."Asset Code"
Ref: "waltz"."application"."lifecycle_phase" > "waltzUI"."Application Summary"."Lifecycle Phase"
Ref: "waltz"."application"."kind" > "waltzUI"."Application Summary"."Type"
Ref: "waltz"."organisational_unit"."name" > "waltzUI"."Application Summary"."Owning Org Unit"
Ref: "waltz"."application"."overall_rating" > "waltzUI"."Application Summary"."Overall Rating"
Ref: "waltz"."entity_alias"."alias" > "waltzUI"."Application Summary"."Alias"
Ref: "waltz"."tag"."name" > "waltzUI"."Application Summary"."Tags"
Ref: "waltz"."complexity"."score" > "waltzUI"."Application Summary"."Complexity Rating"
Ref: "waltz"."application"."business_criticality" > "waltzUI"."Application Summary"."Business Criticality"
Ref: "waltz"."application"."planned_retirement_date" > "waltzUI"."Application Summary"."Planned Retirement Date"
Ref: "waltz"."application"."actual_retirement_date" > "waltzUI"."Application Summary"."Actual Retirement Date"
Ref: "waltz"."application"."commission_date" > "waltzUI"."Application Summary"."Commission Date"
Ref: "waltz"."application"."provenance" > "waltzUI"."Application Summary"."Provenance"
Ref: "waltz"."application"."description" > "waltzUI"."Application Summary"."Description"

```