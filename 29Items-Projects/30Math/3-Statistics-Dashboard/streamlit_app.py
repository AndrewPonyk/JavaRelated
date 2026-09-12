"""Streamlit entrypoint.

Streamlit Cloud runs this file (configure it as the app's "main file"). Pages are
registered programmatically with st.navigation so page modules stay plain,
importable functions — testable headlessly via streamlit.testing.v1.AppTest.
"""

import streamlit as st

from app.core.logging import setup_logging
from app.ui.pages import (
    ab_testing,
    data_explorer,
    distribution_fitting,
    experiments,
    home,
    hypothesis_testing,
    regression,
)

setup_logging()

st.set_page_config(
    page_title="Statistics Dashboard",
    page_icon="📊",
    layout="wide",
    initial_sidebar_state="expanded",
)

navigation = st.navigation(
    {
        "Overview": [
            st.Page(home.render, title="Home", icon="🏠", url_path="home", default=True),
        ],
        "Explore": [
            st.Page(
                data_explorer.render,
                title="Data Explorer",
                icon="🔍",
                url_path="data-explorer",
            ),
        ],
        "Analyze": [
            st.Page(
                hypothesis_testing.render,
                title="Hypothesis Testing",
                icon="🧪",
                url_path="hypothesis-testing",
            ),
            st.Page(
                regression.render,
                title="Regression",
                icon="📈",
                url_path="regression",
            ),
            st.Page(
                distribution_fitting.render,
                title="Distribution Fitting",
                icon="📉",
                url_path="distribution-fitting",
            ),
            st.Page(
                ab_testing.render,
                title="A/B Testing",
                icon="⚖️",
                url_path="ab-testing",
            ),
        ],
        "Manage": [
            st.Page(
                experiments.render,
                title="Experiments",
                icon="⚗️",
                url_path="experiments",
            ),
        ],
    }
)
navigation.run()
