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
package org.sonar.server.project.ws;

import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.api.config.MapSettings;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.ResourceType;
import org.sonar.api.resources.ResourceTypes;
import org.sonar.api.rule.RuleKey;
import org.sonar.api.security.DefaultGroups;
import org.sonar.api.utils.System2;
import org.sonar.api.web.UserRole;
import org.sonar.db.DbClient;
import org.sonar.db.DbSession;
import org.sonar.db.DbTester;
import org.sonar.db.component.ComponentDto;
import org.sonar.db.component.ComponentTesting;
import org.sonar.db.component.SnapshotDto;
import org.sonar.db.component.SnapshotTesting;
import org.sonar.db.issue.IssueDto;
import org.sonar.db.issue.IssueTesting;
import org.sonar.db.rule.RuleDto;
import org.sonar.db.rule.RuleTesting;
import org.sonar.server.component.ComponentCleanerService;
import org.sonar.server.component.ComponentFinder;
import org.sonar.server.component.index.ComponentIndex;
import org.sonar.server.component.index.ComponentIndexDefinition;
import org.sonar.server.component.index.ComponentIndexQuery;
import org.sonar.server.component.index.ComponentIndexer;
import org.sonar.server.es.EsTester;
import org.sonar.server.exceptions.ForbiddenException;
import org.sonar.server.issue.IssueDocTesting;
import org.sonar.server.issue.index.IssueAuthorizationDoc;
import org.sonar.server.issue.index.IssueIndexDefinition;
import org.sonar.server.issue.index.IssueIndexer;
import org.sonar.server.measure.index.ProjectMeasuresIndexer;
import org.sonar.server.permission.index.PermissionIndexerTester;
import org.sonar.server.test.index.TestDoc;
import org.sonar.server.test.index.TestIndexDefinition;
import org.sonar.server.test.index.TestIndexer;
import org.sonar.server.tester.UserSessionRule;
import org.sonar.server.ws.WsTester;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.guava.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.server.project.ws.DeleteAction.PARAM_ID;
import static org.sonar.server.project.ws.DeleteAction.PARAM_KEY;

public class DeleteActionTest {

  private static final String ACTION = "delete";

  private System2 system2 = System2.INSTANCE;

  @Rule
  public DbTester db = DbTester.create(system2);

  @Rule
  public EsTester es = new EsTester(
    new IssueIndexDefinition(new MapSettings()),
    new TestIndexDefinition(new MapSettings()),
    new ComponentIndexDefinition(new MapSettings()));

  @Rule
  public UserSessionRule userSessionRule = UserSessionRule.standalone();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private WsTester ws;
  private DbClient dbClient = db.getDbClient();
  private final DbSession dbSession = db.getSession();
  private ResourceType resourceType;
  public ComponentIndex componentIndex;
  public ComponentIndexer componentIndexer;
  private PermissionIndexerTester permissionIndexerTester;

  @Before
  public void setUp() {
    resourceType = mock(ResourceType.class);
    when(resourceType.getBooleanProperty(anyString())).thenReturn(true);
    ResourceTypes mockResourceTypes = mock(ResourceTypes.class);
    when(mockResourceTypes.get(anyString())).thenReturn(resourceType);

    componentIndex = new ComponentIndex(es.client(), userSessionRule);
    componentIndexer = new ComponentIndexer(dbClient, es.client());
    permissionIndexerTester = new PermissionIndexerTester(es);

    ws = new WsTester(new ProjectsWs(
      new DeleteAction(
        new ComponentCleanerService(
          dbClient,
          new IssueIndexer(system2, dbClient, es.client()),
          new TestIndexer(system2, dbClient, es.client()),
          new ProjectMeasuresIndexer(system2, dbClient, es.client()),
          componentIndexer,
          mockResourceTypes,
          new ComponentFinder(dbClient)),
        new ComponentFinder(dbClient),
        dbClient,
        userSessionRule)));
    userSessionRule.login("login").setGlobalPermissions(UserRole.ADMIN);
  }

  @Test
  public void delete_project_and_data_in_db_by_uuid() throws Exception {
    long snapshotId1 = insertNewProjectInDbAndReturnSnapshotId(1);
    long snapshotId2 = insertNewProjectInDbAndReturnSnapshotId(2);

    newRequest()
      .setParam(PARAM_ID, "project-uuid-1").execute();
    dbSession.commit();

    assertThat(dbClient.componentDao().selectByUuid(dbSession, "project-uuid-1")).isAbsent();
    assertThat(dbClient.componentDao().selectOrFailByUuid(dbSession, "project-uuid-2")).isNotNull();
    assertThat(dbClient.snapshotDao().selectById(dbSession, snapshotId1)).isNull();
    assertThat(dbClient.snapshotDao().selectById(dbSession, snapshotId2)).isNotNull();
    assertThat(dbClient.issueDao().selectByKey(dbSession, "issue-key-1").isPresent()).isFalse();
    assertThat(dbClient.issueDao().selectOrFailByKey(dbSession, "issue-key-2")).isNotNull();
  }

  @Test
  public void delete_projects_and_data_in_db_by_key() throws Exception {
    insertNewProjectInDbAndReturnSnapshotId(1);
    insertNewProjectInDbAndReturnSnapshotId(2);

    newRequest()
      .setParam(PARAM_KEY, "project-key-1").execute();
    dbSession.commit();

    assertThat(dbClient.componentDao().selectByUuid(dbSession, "project-uuid-1")).isAbsent();
    assertThat(dbClient.componentDao().selectOrFailByUuid(dbSession, "project-uuid-2")).isNotNull();
  }

  @Test
  public void delete_projects_by_uuid_when_admin_on_the_project() throws Exception {
    insertNewProjectInDbAndReturnSnapshotId(1);
    userSessionRule.login("login").addProjectUuidPermissions(UserRole.ADMIN, "project-uuid-1");

    newRequest().setParam(PARAM_ID, "project-uuid-1").execute();
    dbSession.commit();

    assertThat(dbClient.componentDao().selectByUuid(dbSession, "project-uuid-1")).isAbsent();
  }

  @Test
  public void delete_projects_by_key_when_admin_on_the_project() throws Exception {
    insertNewProjectInDbAndReturnSnapshotId(1);
    // can't use addProjectUuidPermissions as mock keep separated lists
    userSessionRule.login("login").addProjectPermissions(UserRole.ADMIN, "project-key-1");

    newRequest().setParam(PARAM_KEY, "project-key-1").execute();
    dbSession.commit();

    assertThat(dbClient.componentDao().selectByUuid(dbSession, "project-uuid-1")).isAbsent();
  }

  @Test
  public void delete_documents_indexes() throws Exception {
    insertNewProjectInIndexes(1);
    insertNewProjectInIndexes(2);

    assertComponentIndexSearchResults("project-key-1", "project-uuid-1");

    newRequest()
      .setParam(PARAM_KEY, "project-key-1").execute();

    String remainingProjectUuid = "project-uuid-2";
    assertThat(es.getDocumentFieldValues(IssueIndexDefinition.INDEX, IssueIndexDefinition.TYPE_ISSUE, IssueIndexDefinition.FIELD_ISSUE_PROJECT_UUID))
      .containsOnly(remainingProjectUuid);
    assertThat(es.getIds(IssueIndexDefinition.INDEX, IssueIndexDefinition.TYPE_AUTHORIZATION)).containsOnly(remainingProjectUuid);
    assertThat(es.getDocumentFieldValues(TestIndexDefinition.INDEX, TestIndexDefinition.TYPE, TestIndexDefinition.FIELD_PROJECT_UUID))
      .containsOnly(remainingProjectUuid);

    assertComponentIndexSearchResults("project-key-1" /* empty list of results expected */);
  }

  private void assertComponentIndexSearchResults(String query, String... expectedResultUuids) {
    assertThat(componentIndex.search(new ComponentIndexQuery(query))).containsExactly(expectedResultUuids);
  }

  @Test
  public void web_service_returns_204() throws Exception {
    insertNewProjectInDbAndReturnSnapshotId(1);

    WsTester.Result result = newRequest().setParam(PARAM_ID, "project-uuid-1").execute();

    result.assertNoContent();
  }

  @Test
  public void fail_if_insufficient_privileges() throws Exception {
    userSessionRule.setGlobalPermissions(UserRole.CODEVIEWER, UserRole.ISSUE_ADMIN, UserRole.USER);
    expectedException.expect(ForbiddenException.class);

    newRequest().setParam(PARAM_ID, "whatever-the-uuid").execute();
  }

  @Test
  public void fail_if_scope_is_not_project() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    dbClient.componentDao().insert(dbSession, ComponentTesting.newFileDto(ComponentTesting.newProjectDto(db.getDefaultOrganization()), null, "file-uuid"));
    dbSession.commit();

    newRequest().setParam(PARAM_ID, "file-uuid").execute();
  }

  @Test
  public void fail_if_qualifier_is_not_deletable() throws Exception {
    expectedException.expect(IllegalArgumentException.class);
    dbClient.componentDao().insert(dbSession, ComponentTesting.newProjectDto(db.organizations().insert(), "project-uuid").setQualifier(Qualifiers.FILE));
    dbSession.commit();
    when(resourceType.getBooleanProperty(anyString())).thenReturn(false);

    newRequest().setParam(PARAM_ID, "project-uuid").execute();
  }

  private long insertNewProjectInDbAndReturnSnapshotId(int id) {
    String suffix = String.valueOf(id);
    ComponentDto project = ComponentTesting
      .newProjectDto(db.organizations().insert(), "project-uuid-" + suffix)
      .setKey("project-key-" + suffix);
    RuleDto rule = RuleTesting.newDto(RuleKey.of("sonarqube", "rule-" + suffix));
    dbClient.ruleDao().insert(dbSession, rule);
    IssueDto issue = IssueTesting.newDto(rule, project, project).setKee("issue-key-" + suffix);
    dbClient.componentDao().insert(dbSession, project);
    SnapshotDto snapshot = dbClient.snapshotDao().insert(dbSession, SnapshotTesting.newAnalysis(project));
    dbClient.issueDao().insert(dbSession, issue);
    dbSession.commit();

    return snapshot.getId();
  }

  private void insertNewProjectInIndexes(int id) throws Exception {
    String suffix = String.valueOf(id);
    ComponentDto project = ComponentTesting
      .newProjectDto(db.getDefaultOrganization(), "project-uuid-" + suffix)
      .setKey("project-key-" + suffix);
    dbClient.componentDao().insert(dbSession, project);
    dbSession.commit();

    es.putDocuments(IssueIndexDefinition.INDEX, IssueIndexDefinition.TYPE_ISSUE, IssueDocTesting.newDoc("issue-key-" + suffix, project));
    es.putDocuments(IssueIndexDefinition.INDEX, IssueIndexDefinition.TYPE_AUTHORIZATION, new IssueAuthorizationDoc().setProjectUuid(project.uuid()));

    componentIndexer.indexByProjectUuid(project.uuid());
    permissionIndexerTester.indexProjectPermission(project.uuid(),
      Collections.singletonList(DefaultGroups.ANYONE),
      emptyList());

    TestDoc testDoc = new TestDoc().setUuid("test-uuid-" + suffix).setProjectUuid(project.uuid()).setFileUuid(project.uuid());
    es.putDocuments(TestIndexDefinition.INDEX, TestIndexDefinition.TYPE, testDoc);
  }

  private WsTester.TestRequest newRequest() {
    return ws.newPostRequest(ProjectsWs.ENDPOINT, ACTION);
  }
}
