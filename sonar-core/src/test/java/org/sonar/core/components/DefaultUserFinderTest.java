/*
 * Sonar, open source software quality management tool.
 * Copyright (C) 2008-2011 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * Sonar is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * Sonar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Sonar; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.core.components;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.sonar.api.database.model.User;
import org.sonar.api.security.UserFinder;
import org.sonar.jpa.test.AbstractDbUnitTestCase;

public class DefaultUserFinderTest extends AbstractDbUnitTestCase {

  private UserFinder userFinder;

  @Before
  public void setUp() {
    setupData("fixture");
    userFinder = new DefaultUserFinder(getSessionFactory());
  }

  @Test
  public void shouldFindUserByLogin() {
    User user = userFinder.findByLogin("simon");
    assertThat(user.getId(), is(1));
    assertThat(user.getLogin(), is("simon"));
    assertThat(user.getName(), is("Simon Brandhof"));
    assertThat(user.getEmail(), is("simon.brandhof@sonarsource.com"));

    user = userFinder.findByLogin("godin");
    assertThat(user.getId(), is(2));
    assertThat(user.getLogin(), is("godin"));
    assertThat(user.getName(), is("Evgeny Mandrikov"));
    assertThat(user.getEmail(), is("evgeny.mandrikov@sonarsource.com"));
  }

  @Test
  public void userNotExists() {
    User user = userFinder.findByLogin("user");
    assertThat(user, nullValue());
  }

}
