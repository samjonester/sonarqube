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
package org.sonar.scanner.bootstrap;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.sonar.api.BatchExtension;
import org.sonar.api.batch.BuildBreaker;
import org.sonar.api.batch.CheckProject;
import org.sonar.api.batch.Decorator;
import org.sonar.api.batch.DependedUpon;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.Phase;
import org.sonar.api.batch.PostJob;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.batch.fs.internal.DefaultInputModule;
import org.sonar.api.batch.fs.internal.InputModuleHierarchy;
import org.sonar.api.batch.postjob.PostJobContext;
import org.sonar.api.resources.Project;
import org.sonar.core.platform.ComponentContainer;
import org.sonar.scanner.bootstrap.ScannerExtensionDictionnary;
import org.sonar.scanner.bootstrap.ExtensionMatcher;
import org.sonar.scanner.postjob.PostJobOptimizer;
import org.sonar.scanner.sensor.DefaultSensorContext;
import org.sonar.scanner.sensor.SensorOptimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class ScannerExtensionDictionnaryTest {

  private ScannerExtensionDictionnary newSelector(Object... extensions) {
    ComponentContainer iocContainer = new ComponentContainer();
    for (Object extension : extensions) {
      iocContainer.addSingleton(extension);
    }
    return new ScannerExtensionDictionnary(mock(InputModuleHierarchy.class), iocContainer, mock(DefaultSensorContext.class), mock(SensorOptimizer.class),
      mock(PostJobContext.class),
      mock(PostJobOptimizer.class));
  }

  @Test
  public void testGetFilteredExtensionWithExtensionMatcher() {
    final Sensor sensor1 = new FakeSensor();
    final Sensor sensor2 = new FakeSensor();

    ScannerExtensionDictionnary selector = newSelector(sensor1, sensor2);
    Collection<Sensor> sensors = selector.select(Sensor.class, null, true, new ExtensionMatcher() {
      @Override
      public boolean accept(Object extension) {
        return extension.equals(sensor1);
      }
    });

    assertThat(sensors).contains(sensor1);
    assertEquals(1, sensors.size());
  }

  @Test
  public void testGetFilteredExtensions() {
    Sensor sensor1 = new FakeSensor();
    Sensor sensor2 = new FakeSensor();
    Decorator decorator = mock(Decorator.class);

    ScannerExtensionDictionnary selector = newSelector(sensor1, sensor2, decorator);
    Collection<Sensor> sensors = selector.select(Sensor.class, null, true, null);

    assertThat(sensors).containsOnly(sensor1, sensor2);
  }

  @Test
  public void shouldSearchInParentContainers() {
    Sensor a = new FakeSensor();
    Sensor b = new FakeSensor();
    Sensor c = new FakeSensor();

    ComponentContainer grandParent = new ComponentContainer();
    grandParent.addSingleton(a);

    ComponentContainer parent = grandParent.createChild();
    parent.addSingleton(b);

    ComponentContainer child = parent.createChild();
    child.addSingleton(c);

    ScannerExtensionDictionnary dictionnary = new ScannerExtensionDictionnary(mock(InputModuleHierarchy.class), child, mock(DefaultSensorContext.class),
      mock(SensorOptimizer.class), mock(PostJobContext.class),
      mock(PostJobOptimizer.class));
    assertThat(dictionnary.select(Sensor.class, null, true, null)).containsOnly(a, b, c);
  }

  @Test
  public void sortExtensionsByDependency() {
    BatchExtension a = new MethodDependentOf(null);
    BatchExtension b = new MethodDependentOf(a);
    BatchExtension c = new MethodDependentOf(b);

    ScannerExtensionDictionnary selector = newSelector(b, c, a);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(3);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
    assertThat(extensions.get(2)).isEqualTo(c);
  }

  @Test
  public void useMethodAnnotationsToSortExtensions() {
    BatchExtension a = new GeneratesSomething("foo");
    BatchExtension b = new MethodDependentOf("foo");

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions.size()).isEqualTo(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void methodDependsUponCollection() {
    BatchExtension a = new GeneratesSomething("foo");
    BatchExtension b = new MethodDependentOf(Arrays.asList("foo"));

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void methodDependsUponArray() {
    BatchExtension a = new GeneratesSomething("foo");
    BatchExtension b = new MethodDependentOf(new String[] {"foo"});

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void useClassAnnotationsToSortExtensions() {
    BatchExtension a = new ClassDependedUpon();
    BatchExtension b = new ClassDependsUpon();

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void useClassAnnotationsOnInterfaces() {
    BatchExtension a = new InterfaceDependedUpon() {
    };
    BatchExtension b = new InterfaceDependsUpon() {
    };

    ScannerExtensionDictionnary selector = newSelector(a, b);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // different initial order
    selector = newSelector(b, a);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test
  public void checkProject() {
    BatchExtension ok = new CheckProjectOK();
    BatchExtension ko = new CheckProjectKO();

    ScannerExtensionDictionnary selector = newSelector(ok, ko);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, new DefaultInputModule("foo"), true, null));

    assertThat(extensions).hasSize(1);
    assertThat(extensions.get(0)).isInstanceOf(CheckProjectOK.class);
  }

  @Test
  public void inheritAnnotations() {
    BatchExtension a = new SubClass("foo");
    BatchExtension b = new MethodDependentOf("foo");

    ScannerExtensionDictionnary selector = newSelector(b, a);
    List<BatchExtension> extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);

    // change initial order
    selector = newSelector(a, b);
    extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(2);
    assertThat(extensions.get(0)).isEqualTo(a);
    assertThat(extensions.get(1)).isEqualTo(b);
  }

  @Test(expected = IllegalStateException.class)
  public void annotatedMethodsCanNotBePrivate() {
    ScannerExtensionDictionnary selector = newSelector();
    BatchExtension wrong = new BatchExtension() {
      @DependsUpon
      private Object foo() {
        return "foo";
      }
    };
    selector.evaluateAnnotatedClasses(wrong, DependsUpon.class);
  }

  @Test
  public void dependsUponPhase() {
    BatchExtension pre = new PreSensor();
    BatchExtension analyze = new GeneratesSomething("something");
    BatchExtension post = new PostSensor();

    ScannerExtensionDictionnary selector = newSelector(analyze, post, pre);
    List extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(3);
    assertThat(extensions.get(0)).isEqualTo(pre);
    assertThat(extensions.get(1)).isEqualTo(analyze);
    assertThat(extensions.get(2)).isEqualTo(post);
  }

  @Test
  public void dependsUponInheritedPhase() {
    BatchExtension pre = new PreSensorSubclass();
    BatchExtension analyze = new GeneratesSomething("something");
    BatchExtension post = new PostSensorSubclass();

    ScannerExtensionDictionnary selector = newSelector(analyze, post, pre);
    List extensions = Lists.newArrayList(selector.select(BatchExtension.class, null, true, null));

    assertThat(extensions).hasSize(3);
    assertThat(extensions.get(0)).isEqualTo(pre);
    assertThat(extensions.get(1)).isEqualTo(analyze);
    assertThat(extensions.get(2)).isEqualTo(post);
  }

  @Test
  public void buildStatusCheckersAreExecutedAfterOtherPostJobs() {
    BuildBreaker checker = new BuildBreaker() {
      public void executeOn(Project project, SensorContext context) {
      }
    };

    ScannerExtensionDictionnary selector = newSelector(new FakePostJob(), checker, new FakePostJob());
    List extensions = Lists.newArrayList(selector.select(PostJob.class, null, true, null));

    assertThat(extensions).hasSize(3);
    assertThat(extensions.get(2)).isEqualTo(checker);
  }

  class FakeSensor implements Sensor {

    public void analyse(Project project, SensorContext context) {

    }

    public boolean shouldExecuteOnProject(Project project) {
      return true;
    }
  }

  class MethodDependentOf implements BatchExtension {
    private Object dep;

    MethodDependentOf(Object o) {
      this.dep = o;
    }

    @DependsUpon
    public Object dependsUponObject() {
      return dep;
    }
  }

  @DependsUpon("flag")
  class ClassDependsUpon implements BatchExtension {
  }

  @DependedUpon("flag")
  class ClassDependedUpon implements BatchExtension {
  }

  @DependsUpon("flag")
  interface InterfaceDependsUpon extends BatchExtension {
  }

  @DependedUpon("flag")
  interface InterfaceDependedUpon extends BatchExtension {
  }

  class GeneratesSomething implements BatchExtension {
    private Object gen;

    GeneratesSomething(Object o) {
      this.gen = o;
    }

    @DependedUpon
    public Object generates() {
      return gen;
    }
  }

  class SubClass extends GeneratesSomething {
    SubClass(Object o) {
      super(o);
    }
  }

  @Phase(name = Phase.Name.PRE)
  class PreSensor implements BatchExtension {

  }

  class PreSensorSubclass extends PreSensor {

  }

  @Phase(name = Phase.Name.POST)
  class PostSensor implements BatchExtension {

  }

  class PostSensorSubclass extends PostSensor {

  }

  class CheckProjectOK implements BatchExtension, CheckProject {
    public boolean shouldExecuteOnProject(Project project) {
      return true;
    }
  }

  class CheckProjectKO implements BatchExtension, CheckProject {
    public boolean shouldExecuteOnProject(Project project) {
      return false;
    }
  }

  private class FakePostJob implements PostJob {
    public void executeOn(Project project, SensorContext context) {
    }
  }
}
