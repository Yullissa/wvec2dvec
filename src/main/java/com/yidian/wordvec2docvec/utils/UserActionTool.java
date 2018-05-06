package com.yidian.wordvec2docvec.utils;

import com.hipu.ibcommon.utils.DocidConverter;
import org.testng.collections.Lists;
import yidian.data.morpheus.neo.useraction.*;
import yidian.data.morpheus.neo.useraction.reader.UseractionReader;

import java.util.Comparator;
import java.util.List;

import static yidian.data.morpheus.neo.useraction.reader.UseractionReader.getDefaultFilter;

public class UserActionTool {
    private static volatile UserActionTool instance = null;
    private static UseractionReader reader = null;
    private static String tableName = "useraction";

    public static UserActionTool getInstance() {
        if (instance == null) {
            synchronized (UserActionTool.class) {
                if (instance == null) {
                    instance = new UserActionTool();
                }
            }
        }
        return instance;
    }

    private UserActionTool(){
        try {
            String zkHost = "10.103.8.25:2181,10.103.8.41:2181,10.103.8.33:2181";
            reader = UseractionReader.newBuilder(zkHost, "useraction", "read_tool")
                    .allowKeepConnection(true)
                    .withBorrowTimeout(1000)
                    .withRequestTimeout(1000)
                    .withConnectionTtl(60000)
                    .enableOpenTSDB()
                    .build();
            reader.start();
        } catch (Exception e) {
            System.out.println("build UseractionReader failed " + e);
        }
    }

    public List<String> getUserRecentClicks(String uid){
        List<String> ret = Lists.newArrayList();
        try {
            Filter filter = getDefaultFilter();
            filter.setEvent((short) Event.CLICKDOC.getValue());
            filter.setSource((short) Source.TOPNEWSLISTVIEW.getValue());
            filter.setCtype((short) Ctype.NEWS.getValue());
            filter.setNum(30);
            List<UserAction> result = reader.get(tableName, uid, filter);
            result.sort(Comparator.comparingLong(UserAction::getTimestamp));
            for (UserAction action : result) {
                String docid = DocidConverter.long2Str(action.getDocid());
                ret.add(docid);
//                System.out.println(action);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return ret;
    }

    @Override
    protected void finalize() throws Throwable {
        reader.close();
        super.finalize();
    }

    public static void main(String [] args){
        List<String> docids = UserActionTool.getInstance().getUserRecentClicks("152938318");
    }
}
