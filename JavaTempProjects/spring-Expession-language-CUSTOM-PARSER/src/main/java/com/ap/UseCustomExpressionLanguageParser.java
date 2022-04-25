package com.ap;

import com.ap.domain.Car;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class UseCustomExpressionLanguageParser {
    public static void main(String[] args) {
        System.out.println("Cool parser from Spring");

        ExpressionParser expressionParser = new SpelExpressionParser();

        Expression expression = expressionParser.parseExpression("'Any string'");
        String result = (String) expression.getValue();
        System.out.println("result = " + result);

        Expression expression1 = expressionParser.parseExpression("'Any string'.length()");
        Integer result1 = (Integer) expression1.getValue();
        System.out.println("result1 = " + result1);

        //--------
        Car car = new Car();
        car.setMake("Good manufacturer");
        car.setModel("Model 3");
        car.setYearOfProduction(2014);

        Expression expression2 = expressionParser.parseExpression("model.toLowerCase()");

        EvaluationContext context = new StandardEvaluationContext(car);
        String result2 = (String) expression2.getValue(context);

        System.out.println(result2);
    }
}
