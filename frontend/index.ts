import {createRoot} from 'react-dom/client';
import {createElement} from "react";
import App from "Frontend/App";

createRoot(document.getElementById('outlet')!).render(createElement(App));
