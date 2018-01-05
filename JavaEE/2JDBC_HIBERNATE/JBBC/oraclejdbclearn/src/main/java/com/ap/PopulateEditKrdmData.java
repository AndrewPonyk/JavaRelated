package com.ap;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class PopulateEditKrdmData {
    public static void main(String[] args) throws SQLException, ClassNotFoundException {
        System.out.println("populate edit");


    }

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        Class.forName("oracle.jdbc.driver.OracleDriver");
        Connection connection = null;
        connection = DriverManager.getConnection("jdbc:oracle:thin:@localhost:49161:xe","system","oracle");
        //connection.close();
        return connection;
    }

    static List<String> entityNames;
    static List<String> entityKeys;

    static {
        entityNames = Arrays.asList(
        "MDT_SEASON",
        "MDT_SEASON_TYPE",
        "MDT_SEASON_YR",
        "MDT_BUS_UNIT",
        "MDT_BUS_UNIT_GRP",
        "MDT_BRAND",
        "MDT_BRAND_ASSET",
        "MDT_BRAND_ASSET_TYPE",
        "MDT_BRAND_GRP",
        "MDT_SPORTS_CAT",
        "MDT_COLOR",
        "MDT_CONSTRUCTION_TYPE",
        "MDT_COUNTRY",
        "MDT_COUNTRY_NAME",
        "MDT_COUNTRY_OF_ORIGIN",
        "MDT_CURRENCY",
        "MDT_DEV_LOCN",
        "MDT_DEV_TYPE",
        "MDT_DIRECT_DEVELOPMENT",
        "MDT_GENDER",
        "MDT_GEOGRAPHIC_REGION",
        "MDT_LEAD_TIME_DAYS",
        "MDT_LEAD_TIME_TYPE",
        "MDT_MAIN_MTRL",
        "MDT_MAIN_MTRL_DESC",
        "MDT_MTRL_TECH",
        "MDT_PROD_DIV",
        "MDT_QTY_UNIT",
        "MDT_QUARTER",
        "MDT_SALES_LINE",
        "MDT_SPECIAL_USG",
        "MDT_SPEED_PROP",
        "MDT_TECH_COMP",
        "MDT_TECH_CONCEPT",
        "MDT_TIMELINE",
        "MDT_TOOLING_TYPE",
        "MDT_TOOLING: TOOLING_NU",
        "MDT_UNIT_OF_MEASURE",
        "MDT_AGE_GRP",
        "MDT_ANIMAL_TYPE",
        "MDT_CAT_MKTG_LINE",
        "MDT_BUSINESS_SEGMENT",
        "MDT_BD_PRD_TYP_SP_CAT",
        "MDT_PROD_GRP");

        entityKeys =
                MDT_SEASON: SEASON_CD
        "SEASON_TYPE_CD",
        "SEASON_YR_CD",
        "BUS_UNIT_CD",
        "BUS_UNIT_GRP_CD",
        "BRAND_CD",
        "BRAND_ASSET_CD",
        "BRAND_ASSET_TYPE_CD",
        "BRAND_GRP_CD",
        "SPORTS_CAT_CD",
        "COLOR_CD",
        "CONS_TYP_CD",
        "COUNTRY_CD",
        "COUNTRY_NM_CD",
        "COUNTRY_OF_ORIGIN_CD",
        "CURRENCY_CD",
        "DEV_LOCN_CD",
        "DEV_TYPE_CD",
        "DRCT_DEV_CD",
        "GENDER_CD",
        "GEO_REGION_CD",
        "LT_TYP_DAYS_CD",
        "LT_TYP_CD",
        "MAIN_MTRL_CD",
        "MAIN_MTRL_DESC_CD",
        "MTRL_TECH_CD",
        "PROD_DIV_CD",
        "QTY_UNIT_CD",
        "QUARTER_CD",
        "SALES_LINE_CD",
        "SPECIAL_USG_CD",
        "SPEED_PROP_CD",
        "TECH_COMP_CD",
        "TECH_CONCEPT_CD",
        "TIMELINE_CD",
        "TOOLING_TYPE_CD",
        "TOOLING_NUM,TOOLING_TYPE_CD"
        "UOM_CD",
        "AGE_GRP_CD",
        "ANIMAL_TYPE_CD",
        "CAT_MKTG_LINE_CD",
        "BUS_SEG_CD",
        "PROD_GRP_CD",
        "BRAND_CD"
    }
}
