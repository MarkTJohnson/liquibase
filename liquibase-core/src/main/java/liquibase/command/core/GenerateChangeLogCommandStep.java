package liquibase.command.core;

import liquibase.Scope;
import liquibase.command.CommandArgumentDefinition;
import liquibase.command.CommandResultsBuilder;
import liquibase.command.CommandScope;
import liquibase.command.CommandStepBuilder;
import liquibase.database.Database;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.util.StringUtil;

import java.io.PrintStream;

public class GenerateChangeLogCommandStep extends DiffToChangeLogCommandStep {
    private static final String INFO_MESSAGE =
            "When generating formatted SQL changelogs, it is important to decide if batched statements\n" +
                    "should be split (splitStatements:true is the default behavior) or not (splitStatements:false).\n" +
                    "See http://liquibase.org for additional documentation.";

    public static final CommandArgumentDefinition<String> AUTHOR_ARG;
    public static final CommandArgumentDefinition<String> CONTEXT_ARG;

    static {
        final CommandStepBuilder builder = new CommandStepBuilder(GenerateChangeLogCommandStep.class);

        AUTHOR_ARG = builder.argument("author", String.class).build();
        CONTEXT_ARG = builder.argument("context", String.class).build();

    }

    @Override
    public String[] getName() {
        return new String[]{"generateChangeLog"};
    }

    @Override
    public void run(CommandResultsBuilder resultsBuilder) throws Exception {
        CommandScope commandScope = resultsBuilder.getCommandScope();

        outputBestPracticeMessage();

        String changeLogFile = StringUtil.trimToNull(commandScope.getArgumentValue(CHANGELOG_FILENAME_ARG));
        if (changeLogFile.toLowerCase().endsWith(".sql")) {
            Scope.getCurrentScope().getUI().sendMessage("\n" + INFO_MESSAGE + "\n");
            Scope.getCurrentScope().getLog(getClass()).info("\n" + INFO_MESSAGE + "\n");
        }

        final Database referenceDatabase = commandScope.getArgumentValue(REFERENCE_DATABASE_ARG);

        SnapshotCommandStep.logUnsupportedDatabase(referenceDatabase, this.getClass());

        DiffResult diffResult = createDiffResult(commandScope);

        DiffToChangeLog changeLogWriter = new DiffToChangeLog(diffResult, commandScope.getArgumentValue(DIFF_OUTPUT_CONTROL_ARG));

        changeLogWriter.setChangeSetAuthor(commandScope.getArgumentValue(AUTHOR_ARG));
        changeLogWriter.setChangeSetContext(commandScope.getArgumentValue(CONTEXT_ARG));
        changeLogWriter.setChangeSetPath(changeLogFile);

        ObjectQuotingStrategy originalStrategy = referenceDatabase.getObjectQuotingStrategy();
        try {
            referenceDatabase.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
            if (StringUtil.trimToNull(changeLogFile) != null) {
                changeLogWriter.print(changeLogFile);
            } else {
                PrintStream outputStream = commandScope.getArgumentValue(OUTPUT_STREAM_ARG);
                if (outputStream == null) {
                    outputStream = System.out;
                }
                changeLogWriter.print(outputStream);
            }
        } finally {
            referenceDatabase.setObjectQuotingStrategy(originalStrategy);
        }
    }

    @Override
    protected DatabaseSnapshot createTargetSnapshot(CommandScope commandScope) throws DatabaseException, InvalidExampleException {
        SnapshotControl snapshotControl = new SnapshotControl(commandScope.getArgumentValue(REFERENCE_DATABASE_ARG), commandScope.getArgumentValue(SNAPSHOT_TYPES_ARG));
        return SnapshotGeneratorFactory.getInstance().createSnapshot(commandScope.getArgumentValue(COMPARE_CONTROL_ARG).getSchemas(CompareControl.DatabaseRole.REFERENCE), null, snapshotControl);
    }
}
