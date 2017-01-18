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
package org.sonar.api.resources;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.CheckForNull;

import org.apache.commons.lang.builder.ToStringBuilder;
import org.sonar.api.batch.fs.InputModule;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.InputModuleHierarchy;
import org.sonar.api.component.Component;
import org.sonar.api.scan.filesystem.PathResolver;

/**
 * @since 1.10
 * @deprecated since 5.6 replaced by {@link InputModule}.
 */
@Deprecated
public class Project extends Resource implements Component {
  private final DefaultInputModule module;
  private final InputModuleHierarchy moduleHierarchy;

  public Project(InputModule module, InputModuleHierarchy moduleHierarchy) {
    this.module = (DefaultInputModule) module;
    this.moduleHierarchy = moduleHierarchy;
    this.setKey(module.key());
  }

  public DefaultInputModule inputModule() {
    return module;
  }

  @Override
  public String key() {
    return module.key();
  }

  @Override
  public String path() {
    DefaultInputModule parent = (DefaultInputModule) moduleHierarchy.parent(module);
    if (parent == null) {
      return null;
    }
    return new PathResolver().relativePath(parent.definition().getBaseDir(), module.definition().getBaseDir());
  }

  public String getBranch() {
    return module.definition().getBranch();
  }

  @CheckForNull
  public String getOriginalName() {
    return module.definition().getOriginalName();
  }

  java.io.File getBaseDir() {
    return module.definition().getBaseDir();
  }

  @Override
  public String name() {
    return module.definition().getName();
  }

  @Override
  public String longName() {
    return module.definition().getName();
  }

  @Override
  public String qualifier() {
    return getParent() == null ? Qualifiers.PROJECT : Qualifiers.MODULE;
  }

  @Override
  public String getName() {
    return name();
  }

  public boolean isRoot() {
    return getParent() == null;
  }

  public Project getRoot() {
    return getParent() == null ? this : getParent().getRoot();
  }

  /**
   * @return whether the current project is a module
   */
  public boolean isModule() {
    return !isRoot();
  }

  @Override
  public String getLongName() {
    return longName();
  }

  @Override
  public String getDescription() {
    return module.definition().getDescription();
  }

  /** 
   * @deprecated since 4.2 use {@link org.sonar.api.batch.fs.FileSystem#languages()}
   */
  @Override
  public Language getLanguage() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getScope() {
    return Scopes.PROJECT;
  }

  @Override
  public String getQualifier() {
    return qualifier();
  }

  @Override
  public Project getParent() {
    InputModule parent = moduleHierarchy.parent(module);
    if (parent == null) {
      return null;
    }
    return new Project(parent, moduleHierarchy);
  }

  /**
   * @return the list of modules
   */
  public List<Project> getModules() {
    return moduleHierarchy.children(module).stream()
      .map(c -> new Project(c, moduleHierarchy))
      .collect(Collectors.toList());
  }

  @Override
  public boolean matchFilePattern(String antPattern) {
    return false;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
      .append("id", getId())
      .append("key", key())
      .append("qualifier", getQualifier())
      .toString();
  }

}
