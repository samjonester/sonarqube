/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.ui;

import java.sql.Connection;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import org.sonar.api.Plugin;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.resources.Language;
import org.sonar.api.resources.ResourceType;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.utils.log.Loggers;
import org.sonar.api.web.RubyRailsWebservice;
import org.sonar.core.platform.ComponentContainer;
import org.sonar.core.platform.PluginInfo;
import org.sonar.core.platform.PluginRepository;
import org.sonar.core.timemachine.Periods;
import org.sonar.db.Database;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.property.PropertiesDao;
import org.sonar.db.property.PropertyDto;
import org.sonar.process.ProcessProperties;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.platform.PersistentSettings;
import org.sonar.server.platform.Platform;
import org.sonar.server.platform.db.migration.DatabaseMigrationState;
import org.sonar.server.platform.db.migration.version.DatabaseVersion;
import org.sonar.server.platform.ws.UpgradesAction;

import static com.google.common.collect.Lists.newArrayList;
import static org.sonar.server.platform.db.migration.DatabaseMigrationState.Status;

public final class JRubyFacade {

  private static final JRubyFacade SINGLETON = new JRubyFacade();

  public static JRubyFacade getInstance() {
    return SINGLETON;
  }

  <T> T get(Class<T> componentType) {
    return getContainer().getComponentByType(componentType);
  }

  public Collection<ResourceType> getResourceTypes() {
    return get(ResourceTypes.class).getAllOrdered();
  }

  public ResourceType getResourceType(String qualifier) {
    return get(ResourceTypes.class).get(qualifier);
  }

  public List<String> getQualifiersWithProperty(final String propertyKey) {
    List<String> qualifiers = newArrayList();
    for (ResourceType type : getResourceTypes()) {
      if (type.getBooleanProperty(propertyKey) == Boolean.TRUE) {
        qualifiers.add(type.getQualifier());
      }
    }
    return qualifiers;
  }

  public Collection<String> getResourceLeavesQualifiers(String qualifier) {
    return get(ResourceTypes.class).getLeavesQualifiers(qualifier);
  }

  public Collection<String> getResourceChildrenQualifiers(String qualifier) {
    return get(ResourceTypes.class).getChildrenQualifiers(qualifier);
  }

  // PLUGINS ------------------------------------------------------------------
  public PropertyDefinitions getPropertyDefinitions() {
    return get(PropertyDefinitions.class);
  }

  /**
   * Used for WS api/updatecenter/installed_plugins, to be replaced by api/plugins/installed.
   */
  public Collection<PluginInfo> getPluginInfos() {
    return get(PluginRepository.class).getPluginInfos();
  }

  public Collection<RubyRailsWebservice> getRubyRailsWebservices() {
    return getContainer().getComponentsByType(RubyRailsWebservice.class);
  }

  public Collection<Language> getLanguages() {
    return getContainer().getComponentsByType(Language.class);
  }

  public Database getDatabase() {
    return get(Database.class);
  }

  public boolean isDbUptodate() {
    return getContainer().getComponentByType(DatabaseVersion.class).getStatus() == DatabaseVersion.Status.UP_TO_DATE;
  }

  /* PROFILES CONSOLE : RULES AND METRIC THRESHOLDS */

  public void saveProperty(String key, @Nullable Long componentId, @Nullable Long userId, @Nullable String value) {
    if (componentId == null && userId == null) {
      get(PersistentSettings.class).saveProperty(key, value);
    } else {
      DbClient dbClient = get(DbClient.class);
      PropertiesDao propertiesDao = dbClient.propertiesDao();

      try (DbSession dbSession = dbClient.openSession(false)) {
        if (value == null) {
          propertiesDao.delete(dbSession, new PropertyDto().setKey(key).setResourceId(componentId).setUserId(userId));
        } else {
          propertiesDao.saveProperty(dbSession, new PropertyDto().setKey(key).setResourceId(componentId).setUserId(userId).setValue(value));
        }
        dbSession.commit();
      }
    }
  }

  public String getConfigurationValue(String key) {
    return get(Settings.class).getString(key);
  }

  public Connection getConnection() {
    try {
      return get(Database.class).getDataSource().getConnection();
    } catch (Exception e) {
      /* activerecord does not correctly manage exceptions when connection can not be opened. */
      return null;
    }
  }

  public Object getComponentByClassname(String pluginKey, String className) {
    Plugin plugin = get(PluginRepository.class).getPluginInstance(pluginKey);
    try {
      Class componentClass = plugin.getClass().getClassLoader().loadClass(className);
      return get(componentClass);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(String.format("Class [%s] not found in plugin [%s]", className, pluginKey), e);
    }
  }

  private JRubyI18n getJRubyI18n() {
    return get(JRubyI18n.class);
  }

  public String getMessage(String rubyLocale, String key, String defaultValue, Object... parameters) {
    return getJRubyI18n().message(rubyLocale, key, defaultValue, parameters);
  }

  /*
   * /!\ Used by Views
   */
  public void deleteResourceTree(String projectKey) {
    try {
      get(ComponentCleanerService.class).delete(projectKey);
    } catch (RuntimeException e) {
      Loggers.get(JRubyFacade.class).error("Fail to delete resource with key: " + projectKey, e);
      throw e;
    }
  }

  public void logError(String message) {
    Loggers.get(getClass()).error(message);
  }

  public String getServerHome() {
    return get(Settings.class).getString(ProcessProperties.PATH_HOME);
  }

  public ComponentContainer getContainer() {
    return Platform.getInstance().getContainer();
  }

  public String getPeriodLabel(int periodIndex) {
    return get(Periods.class).label(periodIndex);
  }

  public String getPeriodLabel(String mode, String param, Date date) {
    return get(Periods.class).label(mode, param, date);
  }

  public String getPeriodLabel(String mode, String param, String date) {
    return get(Periods.class).label(mode, param, date);
  }

  public String getPeriodAbbreviation(int periodIndex) {
    return get(Periods.class).abbreviation(periodIndex);
  }

  /**
   * Checks whether the SQ instance is up and running (ie. not in safemode and with an up-to-date database).
   * <p>
   * This method duplicates most of the logic code written in {@link UpgradesAction}
   * class. There is no need to refactor code to avoid this duplication since this method is only used by RoR code
   * which will soon be replaced by pure JS code based on the {@link UpgradesAction}
   * WebService.
   * </p>
   */
  public boolean isSonarAccessAllowed() {
    ComponentContainer container = Platform.getInstance().getContainer();
    DatabaseMigrationState databaseMigrationState = container.getComponentByType(DatabaseMigrationState.class);
    Status migrationStatus = databaseMigrationState.getStatus();
    if (migrationStatus == Status.RUNNING || migrationStatus == Status.FAILED) {
      return false;
    }
    if (migrationStatus == Status.SUCCEEDED) {
      return true;
    }

    DatabaseVersion databaseVersion = container.getComponentByType(DatabaseVersion.class);
    Optional<Long> currentVersion = databaseVersion.getVersion();
    if (!currentVersion.isPresent()) {
      throw new IllegalStateException("Version can not be retrieved from Database. Database is either blank or corrupted");
    }
    DatabaseVersion.Status status = databaseVersion.getStatus();
    if (status == DatabaseVersion.Status.UP_TO_DATE || status == DatabaseVersion.Status.REQUIRES_DOWNGRADE) {
      return true;
    }

    Database database = container.getComponentByType(Database.class);
    return !database.getDialect().supportsMigration();
  }

}
