package uk.gov.gchq.gaffer.federatedstore.util;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.IterableAssert;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

import uk.gov.gchq.gaffer.accumulostore.AccumuloProperties;
import uk.gov.gchq.gaffer.accumulostore.AccumuloStore;
import uk.gov.gchq.gaffer.accumulostore.MiniAccumuloStore;
import uk.gov.gchq.gaffer.accumulostore.key.exception.IteratorSettingException;
import uk.gov.gchq.gaffer.accumulostore.retriever.impl.AccumuloAllElementsRetriever;
import uk.gov.gchq.gaffer.data.element.Edge;
import uk.gov.gchq.gaffer.data.element.Element;
import uk.gov.gchq.gaffer.data.element.function.ElementFilter;
import uk.gov.gchq.gaffer.data.elementdefinition.view.View;
import uk.gov.gchq.gaffer.data.elementdefinition.view.ViewElementDefinition;
import uk.gov.gchq.gaffer.operation.OperationException;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.store.StoreException;
import uk.gov.gchq.gaffer.store.schema.Schema;
import uk.gov.gchq.gaffer.user.StoreUser;
import uk.gov.gchq.koryphe.impl.predicate.IsLessThan;

import java.util.ArrayList;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.ACCUMULO_STORE_SINGLE_USE_PROPERTIES;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.GRAPH_ID_ACCUMULO;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.GROUP_BASIC_EDGE;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.PROPERTY_1;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.SCHEMA_EDGE_BASIC_JSON;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.contextBlankUser;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.edgeBasic;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.loadAccumuloStoreProperties;
import static uk.gov.gchq.gaffer.federatedstore.FederatedStoreTestUtil.loadSchemaFromJson;

class ApplyViewToElementsFunctionTest {


    public static final Schema SCHEMA = loadSchemaFromJson(SCHEMA_EDGE_BASIC_JSON);
    public static final AccumuloProperties ACCUMULO_PROPERTIES = loadAccumuloStoreProperties(ACCUMULO_STORE_SINGLE_USE_PROPERTIES);

    @Test
    public void shouldGetFunctioningIteratorOfAccumuloElementRetriever() throws Exception {
        //given
        final AccumuloStore accumuloStore = getTestStore();
        addEdgeBasic(accumuloStore);

        //when
        final AccumuloAllElementsRetriever elements = new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser());

        //then
        assertThat(elements)
                .isExactlyInstanceOf(AccumuloAllElementsRetriever.class)
                .asInstanceOf(InstanceOfAssertFactories.iterable(Element.class))
                .containsExactly(edgeBasic());
    }

    private static View getViewForEdgeBasic() {
        return new View.Builder().edge(edgeBasic().getGroup()).build();
    }

    private static void addEdgeBasic(final AccumuloStore accumuloStore) throws OperationException {
        accumuloStore.execute(new AddElements.Builder().input(edgeBasic()).build(), contextBlankUser());
    }

    @Test
    public void shouldAggregateEdgesFromMultipleRetrievers() throws Exception {
        //given
        final AccumuloStore accumuloStore = getTestStore();
        addEdgeBasic(accumuloStore);
        AccumuloAllElementsRetriever[] retrievers = getRetrievers(accumuloStore);
        final ApplyViewToElementsFunction function = new ApplyViewToElementsFunction().createFunctionWithContext(makeContext(new View.Builder().edge(GROUP_BASIC_EDGE).build(), SCHEMA.clone()));

        //when
        Iterable<Object> iterable = null;
        for (AccumuloAllElementsRetriever elements : retrievers) {
            iterable = function.apply(elements, iterable);
        }

        //then
        final Edge edge3 = edgeBasic();
        //With aggregated property value of 5
        edge3.putProperty(PROPERTY_1, 5);

        assertThat(iterable)
                .asInstanceOf(InstanceOfAssertFactories.iterable(Element.class))
                .containsExactly(edge3);
    }

    @Test
    public void shouldApplyViewToAggregatedEdgesFromMultipleRetrievers() throws Exception {
        //given
        final AccumuloStore accumuloStore = getTestStore();
        addEdgeBasic(accumuloStore);
        AccumuloAllElementsRetriever[] retrievers = getRetrievers(accumuloStore);
        final ApplyViewToElementsFunction function = new ApplyViewToElementsFunction().createFunctionWithContext(
                makeContext(
                        //Update View to filter OUT greater than 2.
                        new View.Builder().edge(GROUP_BASIC_EDGE,
                                new ViewElementDefinition.Builder()
                                        .postAggregationFilter(new ElementFilter.Builder()
                                                .select(PROPERTY_1)
                                                .execute(new IsLessThan(3))
                                                .build())
                                        .build()).build(),
                        SCHEMA.clone()));

        //when
        Iterable<Object> iterable = null;
        for (AccumuloAllElementsRetriever elements : retrievers) {
            iterable = function.apply(elements, iterable);
        }

        //then
        assertThat(iterable)
                .asInstanceOf(InstanceOfAssertFactories.iterable(Element.class))
                .isEmpty();
    }

    private static AccumuloAllElementsRetriever[] getRetrievers(final AccumuloStore accumuloStore) throws IteratorSettingException, StoreException {
        return new AccumuloAllElementsRetriever[]{
                new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser()),
                new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser()),
                new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser()),
                new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser()),
                new AccumuloAllElementsRetriever(accumuloStore, new GetAllElements.Builder().view(getViewForEdgeBasic()).build(), StoreUser.blankUser())
        };
    }

    private static AccumuloStore getTestStore() throws StoreException {
        final AccumuloStore accumuloStore = new MiniAccumuloStore();
        accumuloStore.initialise(GRAPH_ID_ACCUMULO, SCHEMA.clone(), ACCUMULO_PROPERTIES.clone());
        return accumuloStore;
    }

    private static HashMap<String, Object> makeContext(final View view, final Schema schema) {
        final HashMap<String, Object> map = new HashMap<>();
        map.put(ApplyViewToElementsFunction.VIEW, view);
        map.put(ApplyViewToElementsFunction.SCHEMA, schema);
        return map;
    }

}