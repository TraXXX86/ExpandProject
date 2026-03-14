package fr.expand.project.importdata.api.server;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DataQuerySupportTests {

    @Test
    public void parseLimit_shouldClampToConfiguredMaximum() {
        Assert.assertEquals(200, DataQuerySupport.parseLimit("999", 25));
        Assert.assertEquals(25, DataQuerySupport.parseLimit("0", 25));
        Assert.assertEquals(25, DataQuerySupport.parseLimit("abc", 25));
    }

    @Test
    public void normalizeOperator_shouldFallbackToContains() {
        Assert.assertEquals("equals", DataQuerySupport.normalizeOperator("equals"));
        Assert.assertEquals("equals", DataQuerySupport.normalizeOperator("EQUALS"));
        Assert.assertEquals("contains", DataQuerySupport.normalizeOperator("contains"));
        Assert.assertEquals("contains", DataQuerySupport.normalizeOperator("unsupported"));
    }

    @Test
    public void normalizeMultiValues_shouldSplitCommaSeparatedValuesAndDeduplicate() {
        List<String> values = DataQuerySupport.normalizeMultiValues(
            new String[] {"PERSONNE, ENTREPRISE", "PERSONNE"},
            "ADRESSE,ENTREPRISE"
        );

        Assert.assertEquals(List.of("PERSONNE", "ENTREPRISE", "ADRESSE"), values);
    }
}
