package OOP.Tests.my;

import org.junit.Test;

import OOP.Solution.OOPBefore;

public class OOPBeforeTest
        extends BaseAnnotationTest {

    @Test
    public void targetMethod() {
        assertTargetMethod(OOPBefore.class);
    }

    @Test
    public void retentionRuntime() {
        assertRetentionRuntime(OOPBefore.class);
    }
}
