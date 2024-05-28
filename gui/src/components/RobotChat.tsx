/**
 * @author Lucien Bao
 * @version 0.9
 * @date 20 May 2024
 * RobotChat. Defines a chat interface component for issuing commands and
 * receiving feedback through DIARC.
 */

import React, { useState } from 'react';

import "@chatscope/chat-ui-kit-styles/dist/default/styles.min.css"
import {
    MainContainer,
    ChatContainer,
    MessageList,
    Message,
    MessageInput,
    Conversation,
    ConversationHeader,
    Sidebar,
    Avatar,
    ConversationList
} from "@chatscope/chat-ui-kit-react";

// OOP forever, baybee
class Chat {
    robotName: string;
    robotInfo: string;
    profileImagePath: string;
    messageList: MessageProps[];
    focusThisChat: Function;

    constructor(robotName: string, robotInfo: string, profileImagePath: string,
        messageList: MessageProps[], focusThisChat: Function) {
        this.robotName = robotName;
        this.robotInfo = robotInfo;
        this.profileImagePath = profileImagePath;
        this.messageList = messageList;
        this.focusThisChat = focusThisChat;
    }

    avatar() {
        return (
            <Avatar
                name={this.robotName}
                src={this.profileImagePath}
            />
        );
    }

    conversation(index: number) {
        return (
            <Conversation
                key={index}
                info={this.messageList.length > 0 ?
                    this.messageList.slice(-1)[0].message
                    : "No messages yet"}
                lastSenderName={this.messageList.length > 0 ?
                    this.messageList.slice(-1)[0].sender
                    : null}
                name={this.robotName}
                onClick={(e) => this.focusThisChat(this)}
            >
                {this.avatar()}
            </Conversation>
        );
    }

    conversationHeader() {
        return (
            <ConversationHeader>
                {this.avatar()}
                <ConversationHeader.Content
                    info={this.robotInfo}
                    userName={this.robotName}
                />
            </ConversationHeader>
        );
    }

    renderMessageList() {
        return this.messageList.map(
            (message, index) => <Message key={index} model={message} />)
    }

    postUserMessage(message: string, username: string) {
        this.messageList.push({
            message: message,
            sender: username,
            direction: "outgoing",
            position: "single"
        })
    }

    postServerMessage(message: string) {
        this.messageList.push({
            message: message,
            sender: this.robotName,
            direction: "incoming",
            position: "single"
        })
    }
}

// Subset of 'model' prop at https://chatscope.io/storybook/react/?path=/docs/components-message--docs
type MessageProps = {
    message: string,
    sender: string,
    direction: "incoming" | "outgoing" | 0 | 1,
    position: "single" | "first" | "normal" | "last" | 0 | 1 | 2 | 3
}

// Remove line breaks from messages when sending
const clean = (message: string) => {
    return message.replace("<br>", "").replace(/(\r\n|\n|\r)/gm, "")
}

const RobotChat: React.FC<{}> = () => {
    // SET UP STATE //
    // Pure unadulterated jank
    let [, rerender] = useState({});
    const forceRerender = React.useCallback(() => rerender({}), []);

    const [currentChat, setCurrentChat] =
        useState(new Chat("", "", "", [], () => null));

    const [dempsterChat, setDempsterChat] = useState(
        new Chat("dempster", "NAO robot", "/heroimage.svg", [], setCurrentChat)
    );

    const [shaferChat, setShaferChat] = useState(
        new Chat("shafer", "NAO robot", "/robot.png", [], setCurrentChat)
    );

    const [chats, setChats] = useState([dempsterChat, shaferChat]);

    const [conversations, setConversations] = useState(
        <ConversationList>
            {chats.map((chat, index) => chat.conversation(index))}
        </ConversationList>
    );

    const [username, setUsername] = useState("");

    // SET UP WEBSOCKET //
    const ws = new WebSocket("ws://localhost:8080/chat");
    ws.onmessage = (message) => {
        // console.log(message.data);
        const data = JSON.parse(message.data);
        for (const chat of chats) {
            if (chat.robotName === data.sender) {
                chat.postServerMessage(data.message);
            }
        }
        setConversations(<ConversationList>
            {chats.map((chat, index) => chat.conversation(index))}
        </ConversationList>);
        // We're mutating state so make React render the window again
        forceRerender();
    };
    // ws.onopen = function () { console.log("chat connected"); };
    // ws.onclose = function () { console.log("chat disconnected"); };
    ws.onerror = function (error) { console.error("chat websocket error: " + error); };

    // CREATE RENDER //
    // This is the usual chat container but it only makes sense if there
    // is a conversation already selected...
    let chatContainer = (
        <ChatContainer className='w-3/4'>
            {currentChat.conversationHeader()}

            <MessageList>
                {currentChat.renderMessageList()}
            </MessageList>

            <MessageInput
                autoFocus
                placeholder={"Talk with " + currentChat.robotName
                    + "..."}
                attachButton={false}
                onSend={(message) => {
                    currentChat.postUserMessage(message, username);
                    ws.send(JSON.stringify({
                        message: clean(message),
                        sender: clean(username),
                        recipient: currentChat.robotName
                    }));
                    setConversations(<ConversationList>
                        {chats.map((chat, index) => chat.conversation(index))}
                    </ConversationList>);
                    // Since we're mutating state, we need to trick React
                    // into updating itself
                    forceRerender();
                }}
            />
        </ChatContainer>
    );

    // ...so on startup we just display a welcome message
    if (currentChat.robotName === "") {
        chatContainer = (
            <div className='flex-1 flex items-center justify-center'>
                <p className='text-gray-600'>
                    Select a chat on the left to get started!
                </p>
            </div>
        );
    }

    return (
        <div className='size-9/12'>
            <MainContainer>
                <Sidebar position="left">
                    <div className='flex-col flex space-y-2'>
                        <div className='flex-1 flex items-center \
                                    justify-center flex-col'>
                            <MessageInput
                                className='w-11/12 mt-3'
                                attachButton={false}
                                sendButton={false}
                                placeholder='Set your name here'
                                onChange={(innerHTML, textContent, innerText,
                                    nodes) => setUsername(innerText)}
                                sendOnReturnDisabled={true}
                            />
                        </div>
                        {conversations}
                    </div>
                </Sidebar>

                {chatContainer}

            </MainContainer>
        </div>
    );
}

export default RobotChat;

