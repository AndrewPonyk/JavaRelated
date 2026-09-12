// ============================================================================
//  ui/MainWindow.cpp
//  Dashboard implementation (stub). Lays out the panels and wires the poll
//  timer + kill-switch. Data binding to live engine telemetry is built under RTS_ENABLE_QT.
// ============================================================================
#include "ui/MainWindow.hpp"

#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QTableView>
#include <QTimer>
#include <QVBoxLayout>
#include <QWidget>

namespace rts::ui {

MainWindow::MainWindow(QWidget* parent) : QMainWindow(parent) {
    buildUi();
    connectToEngine();

    pollTimer_ = new QTimer(this);
    connect(pollTimer_, &QTimer::timeout, this, &MainWindow::pollTelemetry);
    pollTimer_->start(50);  // 20 Hz UI refresh; never on the trading path
}

MainWindow::~MainWindow() = default;

void MainWindow::buildUi() {
    setWindowTitle("RTS — Trading Dashboard");

    auto* central = new QWidget(this);
    auto* root    = new QVBoxLayout(central);

    auto* status = new QHBoxLayout();
    connLabel_    = new QLabel("Conn: —", central);
    pnlLabel_     = new QLabel("PnL: 0.00", central);
    latencyLabel_ = new QLabel("p99: — µs", central);
    killSwitch_   = new QPushButton("KILL SWITCH", central);
    killSwitch_->setStyleSheet("background-color:#b00020;color:white;font-weight:bold;");
    status->addWidget(connLabel_);
    status->addWidget(pnlLabel_);
    status->addWidget(latencyLabel_);
    status->addStretch();
    status->addWidget(killSwitch_);

    bookView_ = new QTableView(central);
    blotter_  = new QTableView(central);

    root->addLayout(status);
    root->addWidget(bookView_, /*stretch=*/1);
    root->addWidget(blotter_,  /*stretch=*/2);
    setCentralWidget(central);

    connect(killSwitch_, &QPushButton::clicked, this, &MainWindow::onKillSwitchClicked);
    // Qt wiring: install table models backed by telemetry snapshots.
}

void MainWindow::connectToEngine() {
    // Qt wiring: attach to the engine's shared-memory telemetry ring and open the
    //       authenticated control socket (role-checked commands).
}

void MainWindow::pollTelemetry() {
    // Qt wiring: read latest telemetry snapshot from shared memory and refresh
    //       labels/models. Must be cheap and non-blocking.
}

void MainWindow::onKillSwitchClicked() {
    // Qt wiring: send an idempotent HALT command over the control channel; confirm
    //       acknowledgement and reflect state in the UI.
}

}  // namespace rts::ui
