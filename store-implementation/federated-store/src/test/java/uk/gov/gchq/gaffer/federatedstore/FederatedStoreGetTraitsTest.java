/*
 * Copyright 2021-2022 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.gchq.gaffer.federatedstore;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.access.predicate.AccessPredicate;
import uk.gov.gchq.gaffer.access.predicate.NoAccessPredicate;
import uk.gov.gchq.gaffer.access.predicate.UnrestrictedAccessPredicate;
import uk.gov.gchq.gaffer.accumulostore.AccumuloStore;
import uk.gov.gchq.gaffer.cache.CacheServiceLoader;
import uk.gov.gchq.gaffer.commonutil.StreamUtil;
import uk.gov.gchq.gaffer.federatedstore.operation.FederatedOperation;
import uk.gov.gchq.gaffer.graph.GraphConfig;
import uk.gov.gchq.gaffer.graph.GraphSerialisable;
import uk.gov.gchq.gaffer.store.Context;
import uk.gov.gchq.gaffer.store.StoreProperties;
import uk.gov.gchq.gaffer.store.StoreTrait;
import uk.gov.gchq.gaffer.store.operation.GetTraits;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.store.schema.SchemaEdgeDefinition;
import uk.gov.gchq.gaffer.store.schema.SchemaEntityDefinition;
import uk.gov.gchq.gaffer.user.User;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static uk.gov.gchq.gaffer.user.StoreUser.AUTH_1;
import static uk.gov.gchq.gaffer.user.StoreUser.AUTH_USER_ID;
import static uk.gov.gchq.gaffer.user.StoreUser.TEST_USER_ID;
import static uk.gov.gchq.gaffer.user.StoreUser.UNUSED_AUTH_STRING;
import static uk.gov.gchq.gaffer.user.StoreUser.authUser;
import static uk.gov.gchq.gaffer.user.StoreUser.blankUser;
import static uk.gov.gchq.gaffer.user.StoreUser.nullUser;
import static uk.gov.gchq.gaffer.user.StoreUser.testUser;

public class FederatedStoreGetTraitsTest {

    public static final String GRAPH_ID_ACCUMULO = "accumuloID";
    public static final String GRAPH_ID_MAP = "mapID";
    private static final Set<StoreTrait> ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP = ImmutableSet.of(
            StoreTrait.STORE_VALIDATION,
            StoreTrait.ORDERED);
    private static final Set<StoreTrait> INTERSECTION_TRAITS = ImmutableSet.of(
            StoreTrait.QUERY_AGGREGATION,
            StoreTrait.TRANSFORMATION,
            StoreTrait.PRE_AGGREGATION_FILTERING,
            StoreTrait.VISIBILITY,
            StoreTrait.POST_TRANSFORMATION_FILTERING,
            StoreTrait.INGEST_AGGREGATION,
            StoreTrait.POST_AGGREGATION_FILTERING,
            StoreTrait.MATCHED_VERTEX);
    private static final Set<StoreTrait> MAP_TRAITS_EXCLUSIVE_OF_ACCUMULO = Collections.emptySet();
    private static final FederatedAccess ACCESS_UNUSED_AUTH_AND_USER = new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING);
    private static final FederatedAccess ACCESS_UNUSED_AUTH_WITH_TEST_USER = new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), TEST_USER_ID);
    private static final Set<StoreTrait> MAP_TRAITS = ImmutableSet.of(
            StoreTrait.INGEST_AGGREGATION,
            StoreTrait.MATCHED_VERTEX,
            StoreTrait.POST_AGGREGATION_FILTERING,
            StoreTrait.POST_TRANSFORMATION_FILTERING,
            StoreTrait.PRE_AGGREGATION_FILTERING,
            StoreTrait.QUERY_AGGREGATION,
            StoreTrait.TRANSFORMATION,
            StoreTrait.VISIBILITY);
    private static final Set<StoreTrait> ACCUMULO_TRAITS = AccumuloStore.TRAITS;
    private static final Set<StoreTrait> ACC_CURRENT_TRAITS = ImmutableSet.of(
            StoreTrait.INGEST_AGGREGATION,
            StoreTrait.MATCHED_VERTEX,
            StoreTrait.ORDERED, StoreTrait.POST_AGGREGATION_FILTERING,
            StoreTrait.POST_TRANSFORMATION_FILTERING,
            StoreTrait.PRE_AGGREGATION_FILTERING,
            StoreTrait.TRANSFORMATION);
    private static final Set<StoreTrait> MAP_CURRENT_TRAITS = ImmutableSet.of(
            StoreTrait.INGEST_AGGREGATION,
            StoreTrait.POST_TRANSFORMATION_FILTERING,
            StoreTrait.TRANSFORMATION,
            StoreTrait.POST_AGGREGATION_FILTERING,
            StoreTrait.MATCHED_VERTEX,
            StoreTrait.PRE_AGGREGATION_FILTERING);
    private GetTraits getTraits;
    private AccessPredicate blockingAccessPredicate;
    private AccessPredicate permissiveAccessPredicate;
    private GraphSerialisable acc;
    private GraphSerialisable map;
    private User nullUser;
    private User testUser;
    private User authUser;
    private User blankUser;
    private Context testUserContext;
    private Context authUserContext;
    private Context blankUserContext;
    private static final Set<String> NULL_GRAPH_AUTHS = null;

    private static final StoreProperties MAP_PROPERTIES = StoreProperties.loadStoreProperties(StreamUtil.openStream(FederatedStoreGetTraitsTest.class, "properties/singleUseMapStore.properties"));
    private static final StoreProperties ACCUMULO_PROPERTIES = StoreProperties.loadStoreProperties(StreamUtil.openStream(FederatedStoreGetTraitsTest.class, "properties/singleUseAccumuloStore.properties"));
    private FederatedStore federatedStore;

    @BeforeEach
    public void beforeEach() throws Exception {
        clearCache();
        federatedStore = new FederatedStore();
        federatedStore.initialise("testFed", new Schema(), new FederatedStoreProperties());

        acc = new GraphSerialisable.Builder()
                .config(new GraphConfig(GRAPH_ID_ACCUMULO))
                .properties(ACCUMULO_PROPERTIES)
                .schema(new Schema.Builder()
                        .entity("entities", new SchemaEntityDefinition.Builder()
                                .vertex("string")
                                .build())
                        .type("string", String.class)
                        .build())
                .build();

        map = new GraphSerialisable.Builder()
                .config(new GraphConfig(GRAPH_ID_MAP))
                .properties(MAP_PROPERTIES)
                .schema(new Schema.Builder()
                        .edge("edges", new SchemaEdgeDefinition.Builder()
                                .source("string")
                                .destination("string")
                                .build())
                        .type("string", String.class)
                        .build())
                .build();

        nullUser = nullUser();
        testUser = testUser();
        authUser = authUser();
        blankUser = blankUser();
        testUserContext = new Context(testUser);
        authUserContext = new Context(authUser);
        blankUserContext = new Context(blankUser);

        blockingAccessPredicate = new NoAccessPredicate();
        permissiveAccessPredicate = new UnrestrictedAccessPredicate();

        getTraits = new GetTraits();
    }

    @AfterEach
    public void afterEach() {
        clearCache();
    }

    @Test
    public void shouldVerifyAssumptionsNoTraitsFound() throws Exception {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> federatedStore.execute(getTraits, new Context(nullUser)))
                .withMessage("User is required");
        assertThat(federatedStore.execute(getTraits, new Context(testUser))).isNull();
        assertThat(federatedStore.execute(getTraits, new Context(authUser))).isNull();
        assertThat(federatedStore.execute(getTraits, new Context(blankUser))).isNull();
    }

    @Test
    public void shouldVerifyAssumptionsStoreTraitsNonCurrent() throws Exception {
        // given
        final Set<StoreTrait> mapTraits = map.getGraph().getStoreTraits();
        final Set<StoreTrait> accTraits = acc.getGraph().getStoreTraits();


        // when
        final Set<StoreTrait> mapTraitsExclusive = mapTraits.stream().filter(t -> !accTraits.contains(t)).collect(Collectors.toSet());
        final Set<StoreTrait> accTraitsExclusive = accTraits.stream().filter(t -> !mapTraits.contains(t)).collect(Collectors.toSet());
        final Set<StoreTrait> intersectionTraits = accTraits.stream().filter(mapTraits::contains).collect(Collectors.toSet());

        // then
        assertThat(accTraits).isEqualTo(ACCUMULO_TRAITS).withFailMessage("This store does not have AccumuloStore Traits");
        assertThat(mapTraits).isEqualTo(MAP_TRAITS).withFailMessage("This store does not have MapStore Traits");

        assertThat(mapTraits).isNotEqualTo(accTraits).withFailMessage("Test stores cannot have same traits");

        assertThat(accTraits).hasSize(10).withFailMessage("Expected AccumuloStore trait size is different");
        assertThat(mapTraits).hasSize(8).withFailMessage("Expected MapStore trait size is different");

        assertThat(mapTraitsExclusive).isEqualTo(MAP_TRAITS_EXCLUSIVE_OF_ACCUMULO).withFailMessage("Expected traits exclusive to MapStore is different");
        assertThat(accTraitsExclusive).isEqualTo(ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP).withFailMessage("Expected traits exclusive to AccumuloStore is different");
        assertThat(intersectionTraits).isEqualTo(INTERSECTION_TRAITS).withFailMessage("Expected intersection of traits is different");
    }

    @Test
    public void shouldVerifyAssumptionsStoreTraitsCurrent() throws Exception {
        // given
        getTraits.setCurrentTraits(true);
        final Set<StoreTrait> mapTraitsIsCurrent = map.getGraph().execute(getTraits, testUser);
        final Set<StoreTrait> accTraitsIsCurrent = acc.getGraph().execute(getTraits, testUser);

        // when
        final Set<StoreTrait> mapTraitsIsCurrentExclusive = mapTraitsIsCurrent.stream().filter(t -> !accTraitsIsCurrent.contains(t)).collect(Collectors.toSet());
        final Set<StoreTrait> accTraitsIsCurrentExclusive = accTraitsIsCurrent.stream().filter(t -> !mapTraitsIsCurrent.contains(t)).collect(Collectors.toSet());
        final Set<StoreTrait> intersectionTraitsIsCurrent = accTraitsIsCurrent.stream().filter(mapTraitsIsCurrent::contains).collect(Collectors.toSet());
        final Set<StoreTrait> mapTraitsIsCurrentIsSubSetOfStoreTraits = mapTraitsIsCurrent.stream().filter(t -> !MAP_TRAITS.contains(t)).collect(Collectors.toSet());
        final Set<StoreTrait> accTraitsIsCurrentIsSubSetOfStoreTraits = accTraitsIsCurrent.stream().filter(t -> !ACCUMULO_TRAITS.contains(t)).collect(Collectors.toSet());

        // then
        assertThat(accTraitsIsCurrent).isNotEqualTo(ACCUMULO_TRAITS);
        assertThat(mapTraitsIsCurrent).isNotEqualTo(MAP_TRAITS);

        assertThat(accTraitsIsCurrent).isEqualTo(ACC_CURRENT_TRAITS).withFailMessage("Expected traits for the AccumuloStore 'Current schema' is different");
        assertThat(mapTraitsIsCurrent).isEqualTo(MAP_CURRENT_TRAITS).withFailMessage("Expected traits for the MapStore 'Current schema' is different");

        assertThat(mapTraitsIsCurrentExclusive).withFailMessage("Expected traits exclusive to MapStore is different").isEmpty();

        assertThat(accTraitsIsCurrentExclusive).contains(StoreTrait.ORDERED).withFailMessage("Expected traits exclusive to AccumuloStore is different");
        assertThat(intersectionTraitsIsCurrent).contains(StoreTrait.INGEST_AGGREGATION, StoreTrait.MATCHED_VERTEX, StoreTrait.PRE_AGGREGATION_FILTERING, StoreTrait.TRANSFORMATION, StoreTrait.POST_AGGREGATION_FILTERING, StoreTrait.POST_TRANSFORMATION_FILTERING).withFailMessage("Expected intersection traits is different");

        assertThat(mapTraitsIsCurrentIsSubSetOfStoreTraits).withFailMessage("The IsCurrent traits is not a subset of MapStore traits").isEmpty();
        assertThat(accTraitsIsCurrentIsSubSetOfStoreTraits).withFailMessage("The IsCurrent traits is not a subset of AccumuloStore traits").isEmpty();
    }

    @Test
    public void shouldGetNonCurrentTraitsForAddingUser() throws Exception {
        // given
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_AND_USER, acc);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, map);

        getTraits.setCurrentTraits(false);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).isNotEqualTo(ACCUMULO_TRAITS).withFailMessage("Returning AccumuloStore traits instead of MapStore");
        assertThat(traits.stream().filter(ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP::contains).collect(Collectors.toSet())).withFailMessage("Revealing some hidden traits from the AccumuloStore instead of only MapStore").isEmpty();
        assertThat(traits).isEqualTo(MAP_TRAITS);
    }

    @Test
    public void shouldGetCurrentTraitsForAddingUser() throws Exception {
        // given
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_AND_USER, acc);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, map);
        getTraits.setCurrentTraits(true);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).isNotEqualTo(ACCUMULO_TRAITS).withFailMessage("Returning AccumuloStore traits instead of MapStore");
        assertThat(traits.stream().filter(ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP::contains).collect(Collectors.toSet())).withFailMessage("Revealing some hidden traits from the AccumuloStore instead of only MapStore").isEmpty();
        assertThat(traits).isEqualTo(MAP_CURRENT_TRAITS);
    }

    @Test
    public void shouldGetCurrentTraitsForAddingUserButSelectedGraphsOnly() throws Exception {
        // given
        final GraphSerialisable acc2 = new GraphSerialisable.Builder()
                .graph(acc.getGraph())
                .config(new GraphConfig(GRAPH_ID_ACCUMULO + 2))
                .build();

        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_AND_USER, acc);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, acc2);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).isNotEqualTo(ACCUMULO_TRAITS).withFailMessage("Returning AccumuloStore traits instead of MapStore");
        assertThat(traits.stream().filter(ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP::contains).collect(Collectors.toSet())).withFailMessage("Revealing some hidden traits from the AccumuloStore instead of only MapStore").isEmpty();
        assertThat(traits).isEqualTo(MAP_CURRENT_TRAITS);
    }

    @Test
    public void shouldGetNonCurrentTraitsForAddingUserButSelectedGraphsOnly() throws Exception {
        //given
        final GraphSerialisable acc2 = new GraphSerialisable.Builder()
                .graph(acc.getGraph())
                .config(new GraphConfig(GRAPH_ID_ACCUMULO + 2))
                .build();

        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_AND_USER, acc);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, acc2);
        federatedStore.addGraphs(ACCESS_UNUSED_AUTH_WITH_TEST_USER, map);
        getTraits.setCurrentTraits(false);
        //when
        final Object traits = federatedStore.execute(new FederatedOperation.Builder().op(getTraits).graphIds(GRAPH_ID_MAP).build(), testUserContext);
        //then
        assertNotEquals(ACCUMULO_TRAITS, traits, "Returning AccumuloStore traits instead of MapStore");
        assertEquals(Collections.emptySet(), Stream.of(traits).filter(ACCUMULO_TRAITS_EXCLUSIVE_OF_MAP::contains).collect(Collectors.toSet()), "Revealing some hidden traits from the AccumuloStore instead of only MapStore");
        assertEquals(MAP_TRAITS, Sets.newHashSet((Iterable) traits));
    }

    /**
     * Note:
     * The blockingAccessPredicate will stop ALL access, including Admin.
     * The default federated Read/Write Access predicates are being overridden, here.
     * The predicate controls the logic of how Users and Auths are granted access.
     *
     * @throws Exception exception
     */
    @Test
    public void shouldNotGetTraitsForAddingUserWhenBlockingReadAccessPredicateConfigured() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING), acc);
        federatedStore.addGraphs(new FederatedAccess(NULL_GRAPH_AUTHS, TEST_USER_ID, false, false, blockingAccessPredicate, null), map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).withFailMessage("Revealing hidden traits").isNull();
    }

    @Test
    public void shouldGetTraitsForAuthUser() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING), acc);
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(AUTH_1), testUser.getUserId()), map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, authUserContext);
        // then
        assertThat(traits).isEqualTo(MAP_CURRENT_TRAITS);
    }

    @Test
    public void shouldNotGetTraitsForBlankUser() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING), acc);
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(AUTH_1), TEST_USER_ID), map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, blankUserContext);
        // then
        assertThat(traits).withFailMessage("Revealing hidden traits").isNull();
    }

    @Test
    public void shouldNotGetTraitsForNonAuthUser() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(AUTH_1), AUTH_USER_ID), acc);
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(AUTH_1), AUTH_USER_ID), map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).withFailMessage("Revealing hidden traits").isNull();
    }

    /**
     * Note:
     * The permissiveAccessPredicate will allow ALL access.
     * The default federated Read/Write Access predicates are being overridden, here.
     * The predicate controls the logic of how Users and Auths are granted access.
     *
     * @throws Exception exception
     */
    @Test
    public void shouldGetTraitsForBlankUserWhenPermissiveReadAccessPredicateConfigured() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING), acc);
        federatedStore.addGraphs(new FederatedAccess(NULL_GRAPH_AUTHS, UNUSED_AUTH_STRING, false, false, permissiveAccessPredicate, null), map);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, blankUserContext);
        // then
        assertThat(traits).isEqualTo(MAP_CURRENT_TRAITS);
    }

    /**
     * Note:
     * FederatedStore is Acting like 1 graph (comprised of requested subgraphs),
     * so it can only support the traits shared by all the subgraphs.
     * Traits must return the Intersection of traits for graphs.
     *
     * @throws Exception exception
     */
    @Test
    public void shouldCombineTraitsToMin() throws Exception {
        // given
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING, true), acc);
        federatedStore.addGraphs(new FederatedAccess(Sets.newHashSet(UNUSED_AUTH_STRING), UNUSED_AUTH_STRING, true), map);
        getTraits.setCurrentTraits(false);
        // when
        final Set<StoreTrait> traits = federatedStore.execute(getTraits, testUserContext);
        // then
        assertThat(traits).isEqualTo(INTERSECTION_TRAITS);
    }

    private void clearCache() {
        CacheServiceLoader.shutdown();
    }
}
