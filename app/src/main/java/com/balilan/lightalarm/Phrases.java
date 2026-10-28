package com.balilan.lightalarm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;

/** 内置提醒语库（全部为自写句子或公版古文，可离线使用） */
public class Phrases {

    private static final String[] LIST = {
            "今天到这儿吧，睡个好觉，明天见",
            "手机可以明天再玩，觉不能明天再睡",
            "再刷一会儿，天就亮了",
            "眼睛已经干了，放下手机闭一会儿",
            "早点睡，明天的你会谢谢现在的你",
            "关掉屏幕，世界不会因此少什么",
            "困了就睡，不困也躺下",
            "你已经很久没好好睡觉了",
            "把手机放远一点，睡眠近一点",
            "此刻放下，就是自律",
            "夜深了，安静一会儿",
            "天亮还得早起，早点休息",
            "今天做得够多了，该收工了",
            "屏幕暗下来，心也静下来",
            "别让短视频偷走你的睡眠",
            "闭眼十分钟，胜过刷屏一小时",
            "明天的精力，取决于今晚几点睡",
            "睡觉不是浪费时间，是在充电",
            "你已经很努力了，去休息吧",
            "让手机歇一歇，你也歇一歇",
            "日出而作，日入而息",
            "这一轮结束了，站起来活动一下",
            "抬头看看远处，让眼睛歇一歇",
            "深呼吸三次，再决定要不要继续"
    };

    /** 弹出语库选择，选中后写入 EditText */
    public static void showPicker(final Activity activity, final EditText target) {
        new AlertDialog.Builder(activity)
                .setTitle("从语库选择提醒语")
                .setItems(LIST, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        target.setText(LIST[which]);
                        target.setSelection(target.getText().length());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
