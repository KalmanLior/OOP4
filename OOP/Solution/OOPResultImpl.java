package OOP.Solution;

import OOP.Provided.OOPResult;

public class OOPResultImpl implements OOPResult {
    private OOPTestResult res;
    private String msg;

    public OOPResultImpl() {
        res = null;
        msg = "";
    }

    public OOPResultImpl(OOPTestResult result, String message) {
        res = result;
        msg = message;
    }

    public OOPTestResult getResultType() {
        return res;
    }


    public String getMessage() {
        return msg;
    }

    protected boolean eq(Object obj) {
        if (!(obj instanceof OOPResultImpl))
            return false;
        return (((OOPResultImpl) obj).getMessage().equals(msg)
                && (((OOPResultImpl) obj).getResultType() == res));
    }

    @Override
    public boolean equals(Object obj) {
        return (this.eq(obj) && ((OOPResultImpl) obj).eq(this));
    }

    // TODO: override hashCode?
}
