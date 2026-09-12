// ============================================================================
//  ui/MainWindow.hpp
//  Qt operator dashboard. A CONSUMER of the engine: it reads telemetry from a
//  shared-memory ring and sends control commands (kill-switch, param changes)
//  back over a small TCP channel. It never touches the trading core directly.
//
//  GUI runs on the Qt main thread; engine data crosses the boundary via a
//  poll timer reading the shared-memory ring (or queued signals).
// ============================================================================
#pragma once

#include <QMainWindow>

QT_BEGIN_NAMESPACE
class QTableView;
class QLabel;
class QPushButton;
class QTimer;
QT_END_NAMESPACE

namespace rts::ui {

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget* parent = nullptr);
    ~MainWindow() override;

private slots:
    void pollTelemetry();        // timer-driven read of the shared-mem ring
    void onKillSwitchClicked();  // send halt command over the control channel

private:
    void buildUi();
    void connectToEngine();      // attach shared memory + open control socket

    QTableView*  blotter_{nullptr};      // live orders / fills
    QTableView*  bookView_{nullptr};     // top-of-book depth
    QLabel*      pnlLabel_{nullptr};
    QLabel*      latencyLabel_{nullptr}; // p50/p99 tick-to-trade
    QLabel*      connLabel_{nullptr};    // FIX/feed connection health
    QPushButton* killSwitch_{nullptr};
    QTimer*      pollTimer_{nullptr};
};

}  // namespace rts::ui
