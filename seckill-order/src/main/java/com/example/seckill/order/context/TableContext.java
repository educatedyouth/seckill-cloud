package com.example.seckill.order.context;

public class TableContext {
    private static final ThreadLocal<String> TABLE_NAME_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程要操作的真实表名
     * @param tableName e.g., order_tbl_0
     */
    public static void set(String tableName) {
        TABLE_NAME_HOLDER.set(tableName);
    }

    public static String get() {
        return TABLE_NAME_HOLDER.get();
    }

    public static void clear() {
        TABLE_NAME_HOLDER.remove();
    }
}