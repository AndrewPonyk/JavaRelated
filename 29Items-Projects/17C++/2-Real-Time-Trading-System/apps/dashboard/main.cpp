// ============================================================================
//  apps/dashboard/main.cpp
//  Entry point for the Qt operator dashboard (separate process from the engine).
// ============================================================================
#include <QApplication>

#include "ui/MainWindow.hpp"

int main(int argc, char** argv) {
    QApplication app(argc, argv);
    rts::ui::MainWindow window;
    window.resize(1280, 800);
    window.show();
    return app.exec();
}
