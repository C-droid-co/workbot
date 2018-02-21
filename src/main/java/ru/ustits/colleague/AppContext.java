package ru.ustits.colleague;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.dbutils.QueryRunner;
import org.postgresql.ds.PGSimpleDataSource;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.telegram.telegrambots.bots.commandbot.commands.BotCommand;
import ru.ustits.colleague.commands.*;
import ru.ustits.colleague.commands.repeats.*;
import ru.ustits.colleague.commands.stats.StatsCommand;
import ru.ustits.colleague.commands.stats.WordStatsCmd;
import ru.ustits.colleague.commands.triggers.*;
import ru.ustits.colleague.repositories.*;
import ru.ustits.colleague.repositories.records.RepeatRecord;
import ru.ustits.colleague.repositories.records.StopWordRecord;
import ru.ustits.colleague.repositories.records.TriggerRecord;
import ru.ustits.colleague.repositories.services.MessageService;
import ru.ustits.colleague.repositories.services.RepeatService;
import ru.ustits.colleague.tasks.RepeatScheduler;

import javax.sql.DataSource;

/**
 * @author ustits
 */
@Log4j2
@Configuration
@ComponentScan
@PropertySource("classpath:bot_config.properties")
public class AppContext {

  public static final int MAX_MESSAGE_LENGTH = 4096;

  private static final String ADMIN_PREFIX = "a_";
  private static final String ADD_TRIGGER_COMMAND = "trigger";
  private static final String ADMIN_ADD_TRIGGER_COMMAND = ADMIN_PREFIX + ADD_TRIGGER_COMMAND;
  private static final String TRIGGER_LIST_COMMAND = "trigger_ls";
  private static final String DELETE_TRIGGER_COMMAND = "trigger_rm";
  private static final String ADMIN_DELETE_TRIGGER_COMMAND = ADMIN_PREFIX + DELETE_TRIGGER_COMMAND;
  private static final String ADMIN_DELETE_USER_TRIGGER_COMMAND = ADMIN_DELETE_TRIGGER_COMMAND + "u";
  private static final String HELP_COMMAND = "help";
  private static final String REPEAT_COMMAND = "repeat";
  private static final String ADMIN_REPEAT_COMMAND = ADMIN_PREFIX + "repeat";
  private static final String REPEAT_DAILY_COMMAND = "repeat_d";
  private static final String ADMIN_REPEAT_DAILY_COMMAND = ADMIN_PREFIX + "repeat_d";
  private static final String ADMIN_REPEAT_WORKDAYS_COMMAND = ADMIN_PREFIX + "repeat_wd";
  private static final String REPEAT_WORKDAYS_COMMAND = "repeat_wd";
  private static final String ADMIN_REPEAT_WEEKENDS_COMMAND = ADMIN_PREFIX + "repeat_we";
  private static final String REPEAT_WEEKENDS_COMMAND = "repeat_we";
  private static final String STATS_COMMAND = "stats";
  private static final String WORD_STATS_CMD = "word_stats";
  private static final String PROCESS_STATE_COMMAND = "state_switch";
  private static final String LIST_PROCESS_STATE_COMMAND = "state_ls";
  private static final String SHOW_CURRENT_STATE_COMMAND = "state";
  private static final String CHANGE_TRIGGER_MESSAGE_LENGTH_CMD = ADMIN_PREFIX + "trigger_mes_len";
  private static final String IGNORE_TRIGGERS_CMD = "ignore";

  @Autowired
  private Environment env;

  @Bean
  public ColleagueBot bot(final String botName, final String botToken,
                          final TriggerRepository triggerRepository, final RepeatService repeatService,
                          final MessageService messageService, final Repository<StopWordRecord> stopWordRepository,
                          final IgnoreTriggerRepository ignoreTriggerRepository,
                          final MessageRepository messageRepository, final ChatsRepository chatsRepository,
                          final UserRepository userRepository, final RepeatScheduler scheduler)
          throws SchedulerException {
    final ColleagueBot bot = new ColleagueBot(botName, botToken, messageRepository, chatsRepository, userRepository,
            triggerRepository, repeatService, ignoreTriggerRepository, scheduler);
    bot.registerAll(
            admin(
                    triggerCommand(
                            ADMIN_ADD_TRIGGER_COMMAND,
                            "add trigger to a specific message and chat",
                            new ChatIdParser<>(
                                    new TriggerParser(3, 1)),
                            triggerRepository)),
            triggerCommand(
                    ADD_TRIGGER_COMMAND,
                    "add trigger to a specific message",
                    new TriggerParser(2),
                    triggerRepository),
            helpCommand(bot),
            repeatCommand(
                    REPEAT_COMMAND,
                    "repeat message with cron expression",
                    new PlainParser(),
                    repeatService),
            admin(
                    repeatCommand(
                            ADMIN_REPEAT_COMMAND,
                            "add repeat message with cron expression to any chat",
                            new ChatIdParser<>(
                                    new PlainParser(8, 1)),
                            repeatService)),
            repeatCommand(
                    REPEAT_DAILY_COMMAND,
                    "repeat message everyday",
                    new DailyParser(),
                    repeatService),
            admin(
                    repeatCommand(
                            ADMIN_REPEAT_DAILY_COMMAND,
                            "repeat message everyday (admin)",
                            new ChatIdParser<>(
                                    adminDailyParser()),
                            repeatService)),
            repeatCommand(REPEAT_WORKDAYS_COMMAND, "repeat message every work day",
                    new WorkDaysParser(),
                    repeatService),
            admin(
                    repeatCommand(
                            ADMIN_REPEAT_WORKDAYS_COMMAND,
                            "repeat message every work day (admin)",
                            new ChatIdParser<>(
                                    new WorkDaysParser(
                                            adminDailyParser())),
                            repeatService)),
            repeatCommand(
                    REPEAT_WEEKENDS_COMMAND,
                    "repeat message every weekend",
                    new WeekendsParser(),
                    repeatService),
            admin(
                    repeatCommand(
                            ADMIN_REPEAT_WEEKENDS_COMMAND,
                            "repeat message every weekend (admin)",
                            new ChatIdParser<>(
                                    new WeekendsParser(
                                            adminDailyParser())),
                            repeatService)),
            listTriggersCommand(triggerRepository),
            statsCommand(messageService),
            wordStatsCommand(messageService, stopWordRepository),
            new ArgsAwareCommand(
                    new DeleteTriggerCommand(
                            DELETE_TRIGGER_COMMAND,
                            "delete chat trigger",
                            triggerRepository,
                            new TriggerParser(1)),
                    1),
            admin(
                    new ArgsAwareCommand(
                            new DeleteTriggerCommand(
                                    ADMIN_DELETE_TRIGGER_COMMAND,
                                    "delete admin's trigger for any chat",
                                    triggerRepository,
                                    new ChatIdParser<>(
                                            new TriggerParser(2, 1))),
                            2)),
            admin(
                    new ArgsAwareCommand(
                            new DeleteTriggerCommand(
                                    ADMIN_DELETE_USER_TRIGGER_COMMAND,
                                    "delete any user's trigger for any chat",
                                    triggerRepository,
                                    new ChatIdParser<>(
                                            new UserIdParser<>(
                                                    new TriggerParser(3, 2)))),
                            3)),
            admin(
                    new NoWhitespaceCommand(
                            new ArgsAwareCommand(
                                    new ProcessStateCommand(
                                            PROCESS_STATE_COMMAND,
                                            "change trigger reaction",
                                            bot),
                                    1
                            ))
            ),
            new ListProcessStatesCommand(LIST_PROCESS_STATE_COMMAND, "list all trigger reactions"),
            new ShowStateCommand(
                    SHOW_CURRENT_STATE_COMMAND,
                    "show current trigger reaction",
                    bot),
            admin(
                    new NoWhitespaceCommand(
                            new ArgsAwareCommand(
                                    new ChangeMessageLengthCmd(CHANGE_TRIGGER_MESSAGE_LENGTH_CMD, triggerCmdConfig()),
                                    1
                            )
                    )
            ),
            new IgnoreTriggerCmd(IGNORE_TRIGGERS_CMD, ignoreTriggerRepository)
    );
    return bot;
  }

  private BotCommand triggerCommand(final String command, final String description,
                                    final Parser<TriggerRecord> strategy, final TriggerRepository triggerRepository) {
    return new NoWhitespaceCommand(
            new ArgsAwareCommand(
                    new AddTriggerCommand(command, description, triggerRepository, strategy, triggerCmdConfig()),
                    strategy.parametersCount()));
  }

  private AdminAwareCommand admin(final BotCommand command) {
    return new AdminAwareCommand(command, adminId());
  }

  private DailyParser adminDailyParser() {
    return new DailyParser(4, 1);
  }

  private ListTriggersCommand listTriggersCommand(final TriggerRepository triggerRepository) {
    return new ListTriggersCommand(TRIGGER_LIST_COMMAND, triggerRepository);
  }

  private HelpCommand helpCommand(final ColleagueBot bot) {
    return new HelpCommand(bot, HELP_COMMAND);
  }

  private StatsCommand statsCommand(final MessageService messageService) {
    return new StatsCommand(STATS_COMMAND, messageService);
  }

  private WordStatsCmd wordStatsCommand(final MessageService messageService,
                                        final Repository<StopWordRecord> stopWordRepository) {
    return new WordStatsCmd(WORD_STATS_CMD, messageService, stopWordRepository);
  }

  private BotCommand repeatCommand(final String command, final String description,
                                   final Parser<RepeatRecord> strategy, final RepeatService repeatService)
          throws SchedulerException {
    return new NoWhitespaceCommand(
            new ArgsAwareCommand(
                    new RepeatCommand(command, description, strategy, scheduler(), repeatService),
                    strategy.parametersCount()));
  }

  @Bean
  public TriggerCmdConfig triggerCmdConfig() {
    final TriggerCmdConfig config = new TriggerCmdConfig();
    config.setMessageLength(defaultMessageLength());
    return config;
  }

  @Bean
  public RepeatScheduler scheduler() throws SchedulerException {
    final Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
    scheduler.start();
    return new RepeatScheduler(scheduler);
  }

  @Bean
  public QueryRunner sql(final DataSource dataSource) {
    return new QueryRunner(dataSource);
  }

  @Bean
  public DataSource dataSource() {
    final PGSimpleDataSource dataSource = new PGSimpleDataSource();
    dataSource.setServerName(env.getRequiredProperty("db.url"));
    dataSource.setDatabaseName(env.getRequiredProperty("db.name"));
    dataSource.setUser(env.getRequiredProperty("db.user"));
    dataSource.setPassword(env.getRequiredProperty("db.password"));
    dataSource.setPortNumber(Integer.valueOf(env.getRequiredProperty("db.port")));
    return dataSource;
  }

  @Bean
  public Long adminId() {
    return Long.parseLong(env.getRequiredProperty("admin.id"));
  }

  @Bean
  public String botName() {
    return env.getRequiredProperty("bot.name");
  }

  @Bean
  public String botToken() {
    return env.getRequiredProperty("bot.token");
  }

  @Bean
  public Integer defaultMessageLength() {
    final String raw = env.getProperty("bot.message_length");
    return raw == null ? MAX_MESSAGE_LENGTH : Integer.parseInt(raw);
  }

}
