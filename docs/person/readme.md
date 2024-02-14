# Person Data Loader

![Person Loader Diagram](<src/Person Data Loader with UI.png>)

# Mapping Details

It can be difficult to see the exact mappings in the above diagram. See dbml...

## Mapping to Waltz Database (Person Table)

```
Ref: "waltzloader"."person-out.json"."employee_id" < "waltz"."person"."employee_id"
Ref: "waltzloader"."person-out.json"."display_name" < "waltz"."person"."display_name"
Ref: "waltzloader"."person-out.json"."email" < "waltz"."person"."email"
Ref: "waltzloader"."person-out.json"."user_principal_name" < "waltz"."person"."user_principal_name"
Ref: "waltzloader"."person-out.json"."department_name" < "waltz"."person"."department_name"
Ref: "waltzloader"."person-out.json"."kind" < "waltz"."person"."kind"
Ref: "waltzloader"."person-out.json"."manager_employee_id" < "waltz"."person"."manager_employee_id"
Ref: "waltzloader"."person-out.json"."title" < "waltz"."person"."title"
Ref: "waltzloader"."person-out.json"."office_phone" < "waltz"."person"."office_phone"
Ref: "waltzloader"."person-out.json"."mobile_phone" < "waltz"."person"."mobile_phone"
Ref: "waltzloader"."person-out.json"."organisational_unit_id" < "waltz"."person"."organisational_unit_id"
```

## Mapping to Waltz UI (Person Summary)

```
Ref: "waltz"."organisational_unit"."name" < "waltzUI"."Person Summary"."Organisational Unit"
Ref: "waltz"."person"."organisational_unit_id" < "waltz"."organisational_unit"."id"
Ref: "waltz"."person"."display_name" < "waltzUI"."Person Summary"."(Name)"
Ref: "waltz"."person"."email" < "waltzUI"."Person Summary"."e-mail"
Ref: "waltz"."person"."title" < "waltzUI"."Person Summary"."Title"
Ref: "waltz"."person"."office_phone" < "waltzUI"."Person Summary"."Tel (Office)"
Ref: "waltz"."person"."mobile_phone" < "waltzUI"."Person Summary"."Tel (Mobile)"
Ref: "waltz"."person"."kind" < "waltzUI"."Person Summary"."(Kind)"
Ref: "waltz"."person"."employee_id" < "waltzUI"."Person Summary"."(Employee ID)"
```

## Mapping to Waltz UI (Person Hierarchy)

```
Ref: "waltz"."person"."kind" < "waltzUI"."Person Hierarchy (Managers / Directs)"."(Kind)"
Ref: "waltz"."person"."display_name" < "waltzUI"."Person Hierarchy (Managers / Directs)"."(Name)"
Ref: "waltz"."person"."title" < "waltzUI"."Person Hierarchy (Managers / Directs)"."(Title)"
Ref: "waltz"."person"."email" < "waltzUI"."Person Hierarchy (Managers / Directs)"."(e-mail)"
Ref: "waltz"."person"."office_phone" < "waltzUI"."Person Hierarchy (Managers / Directs)"."(Phone)"
```

## Mapping to Waltz UI (Person Profile)

```
Ref: "waltz"."person"."email" < "waltzUI"."Profile Summary"."Email"
Ref: "waltz"."person"."display_name" < "waltzUI"."Profile Summary"."Name"
Ref: "waltz"."person"."office_phone" < "waltzUI"."Profile Summary"."Phone"
Ref: "waltz"."person"."employee_id" < "waltzUI"."Profile Summary"."Employee Id"
Ref: "waltz"."person"."display_name" < "waltzUI"."Profile Summary"."Managers"
Ref: "waltz"."person"."display_name" < "waltzUI"."Profile Summary"."Direct Reportees"
```