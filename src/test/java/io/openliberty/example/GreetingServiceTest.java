/*
 * Copyright 2016-2017 Red Hat, Inc, IBM, and individual contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.openliberty.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(Arquillian.class)
public class GreetingServiceTest {
	
	private static final String CONFIGMAP_TEST_URL = "http://localhost:9080/configmap-test";

	@Deployment(testable = true)
	public static WebArchive createDeployment() {
		WebArchive archive = ShrinkWrap.create(WebArchive.class, "configmap-test.war")
				.addPackages(true, "io.openliberty.example").addAsManifestResource("microprofile-config.properties");
		return archive;
	}
	
    @Test
    @RunAsClient
    public void resource() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(CONFIGMAP_TEST_URL)
                .path("api")
                .path("greeting");

        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Hello World from a unit test file"));
    }

    @Test
    @RunAsClient
    public void resourceWithParam() {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(CONFIGMAP_TEST_URL)
                .path("api")
                .path("greeting")
                .queryParam("name", "Peter");

        Response response = target.request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, response.getStatus());
        assertTrue(response.readEntity(String.class).contains("Hello Peter from a unit test file"));
    }
}
