package OOP.Solution;

import OOP.Provided.OOPResult;
import java.util.Map;

public class OOPTestSummary {
    private Map<String, OOPResult> testsMap;
    public OOPTestSummary(Map<String, OOPResult> testMap){
        testsMap = testMap;
    }
    public int getNumSuccesses(){
        return getNumByFilter(OOPResult.OOPTestResult.SUCCESS);
    }
    public int getNumFailures(){
        return getNumByFilter(OOPResult.OOPTestResult.FAILURE);
    }
    public int getNumExceptionMismatches(){
        return getNumByFilter(OOPResult.OOPTestResult.EXPECTED_EXCEPTION_MISMATCH);
    }
    public int getNumErrors(){
        return getNumByFilter(OOPResult.OOPTestResult.ERROR);
    }

    private int getNumByFilter(OOPResult.OOPTestResult res){
        return (int)testsMap.values()
                .stream()
                .filter(oopResult -> oopResult.getResultType() == res)
                .count();
    }
}
