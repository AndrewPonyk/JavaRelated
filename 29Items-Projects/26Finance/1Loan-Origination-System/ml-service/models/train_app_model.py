import pandas as pd
import xgboost as xgb

# ТРЕНУВАННЯ МОДЕЛІ ДЛЯ 7 АТРИБУТІВ (Сирі категорії)
# Використовуйте цю шпаргалку для мапінгу у вашій апці:
#
# 1. annualIncome (Index 0): 1:<0 DM, 2:0-200, 3:>=200/Зарплата, 4:Немає
# 2. existingDebt (Index 10): 1:Банки, 2:Магазини, 3:Немає
# 3. loanAmount (Index 3): Сума в німецьких марках (число)
# 4. employmentYears (Index 5): 1:0р, 2:<1р, 3:1-4р, 4:4-7р, 5:>7р
# 5. age (Index 9): Вік (число)
# 6. numPreviousLoans (Index 12): Кількість кредитів (число)
# 7. numDelinquencies (Index 2): 0,1,2:Немає, 3:Були затримки, 4:Критично

# 1. Завантаження даних
file_path = "german.data-numeric.txt"
df = pd.read_csv(file_path, sep='\\s+', header=None)

# 2. Вибір 7 атрибутів
selected_indices = [0, 10, 3, 5, 9, 12, 2]
column_names = [
    "annualIncome",
    "existingDebt",
    "loanAmount",
    "employmentYears",
    "age",
    "numPreviousLoans",
    "numDelinquencies"
]

X = df.iloc[:, selected_indices]
X.columns = column_names
y = df.iloc[:, 24] - 1  # 0=Good, 1=Bad

# 3. Навчання
model = xgb.XGBClassifier(
    n_estimators=100,
    max_depth=5,
    learning_rate=0.1,
    random_state=42,
    eval_metric='logloss'
)

print(f"🚀 Тренування на колонках: {selected_indices}")
model.fit(X, y)

# 4. Збереження
model.save_model("loan_model.json")
print("✅ loan_model.json збережено.")

# 5. Приклад перевірки (Ваш мапінг)
test_client = pd.DataFrame([{
    "annualIncome": 3,         # 150k -> Стабільна зарплата
    "existingDebt": 3,         # Немає
    "loanAmount": 100000000,        # Мала сума
    "employmentYears": 5,      # >7 років
    "age": 35,
    "numPreviousLoans": 1,
    "numDelinquencies": 2      # Виплачує вчасно (0 прострочок)
}])

prob = model.predict_proba(test_client)[0][1]
decision = "✅ СХВАЛЕНО" if prob < 0.35 else "❌ ВІДМОВА"

print(f"\n--- ПЕРЕВІРКА ---")
print(f"Ймовірність ризику: {prob*100:.2f}%")
print(f"Рішення: {decision}")

print("\n--- ВАЖЛИВІСТЬ АТРИБУТІВ (Feature Importance) ---")
importances = dict(zip(X.columns, model.feature_importances_))
for feature, val in sorted(importances.items(), key=lambda x: x[1], reverse=True):
    print(f"{feature}: {val*100:.2f}%")
