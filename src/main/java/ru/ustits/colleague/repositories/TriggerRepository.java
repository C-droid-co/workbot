package ru.ustits.colleague.repositories;

import lombok.extern.log4j.Log4j2;
import ru.ustits.colleague.repositories.records.TriggerRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ustits
 */
@Log4j2
public class TriggerRepository extends BotRepository<String, TriggerRecord> {

  @Override
  public boolean exists(final String entity) {
    return false;
  }

  public boolean exists(final String trigger, final Long chatId, final Long userId) {
    return fetchOne(trigger, chatId, userId) != null;
  }

  public TriggerRecord fetchOne(final String trigger, final Long chatId, final Long userId) {
    try {
      return sql().query("SELECT * FROM triggers WHERE chat_id=? AND user_id=? AND text=?",
              resultSet -> {
                if (resultSet.next()) {
                  return toRecord(resultSet);
                }
                return null;
              },
              chatId, userId, trigger);
    } catch (SQLException e) {
      log.error(e);
    }
    return null;
  }

  public List<TriggerRecord> fetchAll(final Long chatId) {
    try {
      return sql().query("SELECT * FROM triggers WHERE chat_id=?",
              resultSet -> {
                final List<TriggerRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                  records.add(toRecord(resultSet));
                }
                return records;
              },
              chatId);
    } catch (SQLException e) {
      log.error(e);
    }
    return Collections.emptyList();
  }

  @Override
  public TriggerRecord add(final String entity) {
    return null;
  }

  public TriggerRecord add(final String trigger, final String message, final Long chatId, final Long userId) {
    try {
      return sql().insert("INSERT INTO triggers (trigger, message, chat_id, user_id) VALUES (?, ?, ?, ?)",
              resultSet -> {
                resultSet.next();
                final TriggerRecord record = toRecord(resultSet);
                log.info(record);
                return record;
              },
              trigger, message, chatId, userId);
    } catch (SQLException e) {
      log.error(e);
    }
    return null;
  }

  public TriggerRecord update(final String message, final TriggerRecord trigger) {
    try {
      final TriggerRecord record = sql().query("UPDATE triggers SET message=? WHERE trigger=? AND chat_id=? AND user_id=?",
              this::toRecord,
              message, trigger.getTrigger(),
              trigger.getChatId(), trigger.getUserId());
      log.info("Updated trigger: " + record);
      return record;
    } catch (SQLException e) {
      log.error(e);
    }
    return null;
  }

  private TriggerRecord toRecord(final ResultSet resultSet) throws SQLException {
    final Integer id = resultSet.getInt(1);
    final String trigger = resultSet.getString(2);
    final String message = resultSet.getString(3);
    final Long chatId = resultSet.getLong(4);
    final Long userId = resultSet.getLong(5);
    return new TriggerRecord(id, trigger, message, chatId, userId);
  }

}
