import { ChatRoom } from "./components/chat-room.js";

const root = document.querySelector("#app");
const chatRoom = new ChatRoom(root);
chatRoom.start();
window.addEventListener("beforeunload", () => chatRoom.destroy());
