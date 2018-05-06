package com.yidian.wordvec2docvec.core;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

/**
 * Created by admin on 2018/5/4.
 */
public class GetCommonsCli {
    private int DocNum = 0;
    private Double avgle = 0.0;
    private void getCommonscli(String[] args){
        final Options options = new Options();
        options.addOption("DocNum", true, "number of documents");
        options.addOption("avgle",true,"average length of documents");

        final CommandLineParser parser = new PosixParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args,false);
        } catch (final Exception e) {
//            e.printStackTrace();
            System.out.println("in this");
        }

        if (cmd.hasOption("DocNum") && cmd.hasOption("avgle")) {
            DocNum = Integer.parseInt(cmd.getOptionValue("DocNum"));
            avgle = Double.valueOf(cmd.getOptionValue("avgle"));
        }else{
            System.err.println("please input the DocNum");
            System.exit(1);
        }
    }
}
