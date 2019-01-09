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
        boolean tmp = ((OOPResultImpl) obj).getResultType() == res;
        if(((OOPResultImpl) obj).getMessage() != null)
            return (tmp && ((OOPResultImpl) obj).getMessage().equals(msg));
        if(msg != null)
            return false;
        return tmp;
    }

    @Override
    public boolean equals(Object obj) {
        return (this.eq(obj) && ((OOPResultImpl) obj).eq(this));
    }

    @Override
    public int hashCode() {
        try {
            return msg.hashCode();
        }
        catch (Throwable e){
            // msg is null => res = SUCCESS
            return 0;
        }
    }

}
