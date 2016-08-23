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
package org.sonarqube.ws.client.setting;

import org.sonarqube.ws.Settings.ListDefinitionsWsResponse;
import org.sonarqube.ws.client.BaseService;
import org.sonarqube.ws.client.GetRequest;
import org.sonarqube.ws.client.WsConnector;

import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_COMPONENT_ID;
import static org.sonarqube.ws.client.measure.MeasuresWsParameters.PARAM_COMPONENT_KEY;
import static org.sonarqube.ws.client.setting.SettingsWsParameters.ACTION_LIST_DEFINITIONS;
import static org.sonarqube.ws.client.setting.SettingsWsParameters.CONTROLLER_SETTINGS;

public class SettingsService extends BaseService {
  public SettingsService(WsConnector wsConnector) {
    super(wsConnector, CONTROLLER_SETTINGS);
  }

  public ListDefinitionsWsResponse listDefinitions(ListDefinitionsRequest request) {
    GetRequest getRequest = new GetRequest(path(ACTION_LIST_DEFINITIONS))
      .setParam(PARAM_COMPONENT_ID, request.getComponentId())
      .setParam(PARAM_COMPONENT_KEY, request.getComponentKey());
    return call(getRequest, ListDefinitionsWsResponse.parser());
  }

}