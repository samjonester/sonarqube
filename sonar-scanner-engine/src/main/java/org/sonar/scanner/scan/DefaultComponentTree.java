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
package org.sonar.scanner.scan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.InputComponent;
import org.sonar.api.batch.fs.internal.InputComponentTree;

public class DefaultComponentTree implements InputComponentTree {
  private Map<InputComponent, InputComponent> parents = new HashMap<>();
  private Map<InputComponent, List<InputComponent>> children = new HashMap<>();

  public void index(InputComponent component, InputComponent parent) {
    parents.put(component, parent);
    List<InputComponent> list = children.get(parent);
    if (list == null) {
      list = new ArrayList<>();
      children.put(parent, list);
    }

    list.add(component);
  }

  @Override
  public Collection<InputComponent> getChildren(InputComponent component) {
    return children.getOrDefault(component, Collections.emptyList());
  }

  @CheckForNull
  @Override
  public InputComponent getParent(InputComponent component) {
    return parents.get(component);
  }
}
