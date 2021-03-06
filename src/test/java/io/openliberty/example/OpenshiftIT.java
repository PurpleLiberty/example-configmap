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

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.fabric8.openshift.client.OpenShiftClient;
import io.restassured.response.Response;
import org.arquillian.cube.kubernetes.api.Session;
import org.arquillian.cube.openshift.impl.enricher.AwaitRoute;
import org.arquillian.cube.openshift.impl.enricher.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OpenshiftIT {
	private static final String APP_NAME = System.getProperty("app.name");

	private static final String CONFIGMAP_NAME = "app-config";

	@ArquillianResource
	private OpenShiftClient oc;

	@ArquillianResource
	private Session session;

	@RouteURL("${app.name}")
	@AwaitRoute
	private URL baseUrl;

	@RouteURL(value = "${app.name}", path = "/api/greeting")
	private String url;

	@Test
	public void _01_configMapExists() {
		Optional<ConfigMap> configMap = findConfigMap();
		assertTrue(configMap.isPresent());
	}

	@Test
	public void _02_defaultGreeting() {
		await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
			given().baseUri(url).when().get().then().statusCode(200).body(containsString("Hello World from a ConfigMap!"));
		});
	}

	@Test
	public void _03_customGreeting() {
		given().baseUri(url).when().queryParam("name", "Steve").get().then().statusCode(200)
				.body(containsString("Hello Steve from a ConfigMap!"));
	}

	@Test
	public void _04_updateConfigGreeting() throws Exception {
		deployConfigMap("target/test-classes/test-config-update.yml");

		rolloutChanges();

		System.out.println("URL: " + url);
		await().atMost(1, TimeUnit.MINUTES).untilAsserted(() -> {
			given().baseUri(url).when().get().then().statusCode(200)
					.body(containsString("Good morning World from an updated ConfigMap!"));
		});
	}

	@Test
	public void _05_missingConfigurationSource() throws Exception {
		deployConfigMap("target/test-classes/test-config-broken.yml");

		rolloutChanges();

		await().atMost(5, TimeUnit.MINUTES).untilAsserted(() -> {
			given().baseUri(url).when().get().then().statusCode(500);
		});
	}

	private Optional<ConfigMap> findConfigMap() {
		List<ConfigMap> configMaps = oc.configMaps().inNamespace(session.getNamespace()).list().getItems();

		return configMaps.stream().filter(m -> CONFIGMAP_NAME.equals(m.getMetadata().getName())).findAny();
	}

	private void deployConfigMap(String path) throws IOException {
		try (InputStream yaml = new FileInputStream(path)) {
			// in this test, this always replaces an existing configmap, which is already
			// tracked for deleting
			// after the test finishes, so we don't have to care about deleting it
			oc.load(yaml).createOrReplace();
		}
	}

	private void rolloutChanges() {
		System.out.println("Rollout changes to " + APP_NAME);

		// in reality, user would do `oc rollout latest`, but that's hard (racy) to wait
		// for
		// so here, we'll scale down to 0, wait for that, then scale back to 1 and wait
		// again
		scale(APP_NAME, 0);
		scale(APP_NAME, 1);

		await().atMost(5, TimeUnit.MINUTES).until(() -> {
			try {
				Response response = get(baseUrl);
				return response.getStatusCode() == 200;
			} catch (Exception e) {
				return false;
			}
		});
	}

	private void scale(String name, int replicas) {
		oc.deploymentConfigs().inNamespace(session.getNamespace()).withName(name).scale(replicas);

		await().atMost(5, TimeUnit.MINUTES).until(() -> {
			// ideally, we'd look at deployment config's status.availableReplicas field,
			// but that's only available since OpenShift 3.5
			List<Pod> pods = oc.pods().inNamespace(session.getNamespace()).withLabel("deploymentconfig", name).list()
					.getItems();
			try {
				return pods.size() == replicas && pods.stream().allMatch(Readiness::isPodReady);
			} catch (IllegalStateException e) {
				// the 'Ready' condition can be missing sometimes, in which case
				// Readiness.isPodReady throws an exception
				// here, we'll swallow that exception in hope that the 'Ready' condition will
				// appear later
				return false;
			}
		});
	}
}
