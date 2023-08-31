package org.finos.waltz_util.loader;

import org.finos.waltz_util.common.DIBaseConfiguration;
import org.finos.waltz_util.common.helper.DiffResult;
import org.finos.waltz_util.common.helper.LoggingUtilities;
import org.finos.waltz_util.common.model.ApplicationKind;
import org.finos.waltz_util.common.model.Criticality;
import org.finos.waltz_util.schema.tables.records.ApplicationRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record5;
import org.jooq.Result;
import org.jooq.SelectOnConditionStep;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.finos.waltz_util.common.helper.JacksonUtilities.getJsonMapper;
import static org.finos.waltz_util.schema.Tables.APPLICATION;
import static org.finos.waltz_util.schema.Tables.ORGANISATIONAL_UNIT;

public class Main {


    public static final long ORPHAN_ORG_UNIT_ID = 150L;


    public static void main(String[] args) throws IOException {
        ApplicationLoader AL = new ApplicationLoader("apps.json");
        AL.synch();



        PersonLoader PL = new PersonLoader("person.json");
        PL.synch();

        if (true) {
            throw new IOException("NO FURTHER!");
        }

        }

}
