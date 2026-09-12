import WidgetKit
import SwiftUI

struct HabitEntry: TimelineEntry {
    let date: Date
    let title: String
    let streak: Int
    let progress: String
}

struct HabitStreakWidgetEntryView : View {
    var entry: HabitEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Habit Streaks")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.secondary)
                Spacer()
                Text(entry.progress)
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.green)
            }
            Divider()
            Text(entry.title)
                .font(.headline)
                .lineLimit(1)
            HStack {
                Image(systemName: "flame.fill")
                    .foregroundColor(.orange)
                Text("\(entry.streak) day streak")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
        }
        .padding()
        .containerBackground(Color(red: 0.12, green: 0.16, blue: 0.23), for: .widget)
    }
}

struct HabitStreakWidget: Widget {
    let kind: String = "HabitStreakWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: Provider()) { entry in
            HabitStreakWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Habit Streaks")
        .description("Track your daily habits and active streaks.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> HabitEntry {
        HabitEntry(date: Date(), title: "Morning Run", streak: 7, progress: "3/5")
    }

    func getSnapshot(in context: Context, completion: @escaping (HabitEntry) -> ()) {
        completion(placeholder(in: context))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<HabitEntry>) -> ()) {
        let userDefaults = UserDefaults(suiteName: "group.com.example.habitstreaktracker")
        let rawData = userDefaults?.string(forKey: "habit_widget_payload")

        var title = "Keep the streak going!"
        var streak = 0
        var progress = "0/0"

        if let data = rawData?.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            let completed = json["completedCount"] as? Int ?? 0
            let total = json["totalCount"] as? Int ?? 0
            progress = "\(completed)/\(total)"

            if let habits = json["habits"] as? [[String: Any]], let first = habits.first {
                title = first["title"] as? String ?? title
                streak = first["streak"] as? Int ?? 0
            }
        }

        let entry = HabitEntry(date: Date(), title: title, streak: streak, progress: progress)
        let timeline = Timeline(entries: [entry], policy: .atEnd)
        completion(timeline)
    }
}
