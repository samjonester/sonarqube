#
# SonarQube, open source software quality management tool.
# Copyright (C) 2008-2016 SonarSource
# mailto:contact AT sonarsource DOT com
#
# SonarQube is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 3 of the License, or (at your option) any later version.
#
# SonarQube is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public License
# along with this program; if not, write to the Free Software Foundation,
# Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

# All the Java components that are not published to public plugin API.
# Must NOT be used by plugins. Forward-compatibility is NOT guaranteed.
class Internal

  def self.component_api
    component(Java::OrgSonarApiComponent::RubyComponentService.java_class)
  end

  def self.debt
    component(Java::OrgSonarServerDebt::DebtModelService.java_class)
  end

  def self.quality_profiles
    component(Java::OrgSonarServerQualityprofile::QProfiles.java_class)
  end

  def self.qprofile_service
    component(Java::OrgSonarServerQualityprofile::QProfileService.java_class)
  end

  def self.i18n
    component(Java::OrgSonarServerUi::JRubyI18n.java_class)
  end

  def self.component(component_java_class)
    Java::OrgSonarServerPlatform::Platform.component(component_java_class)
  end

end
