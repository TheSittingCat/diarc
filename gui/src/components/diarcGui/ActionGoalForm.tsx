/**
 * @author Lucien Bao
 * @version 0.9
 * @date 5 June 2024
 * ActionGoalForm. Subcomponent which renders a tabbed view of either an action
 * submit form or a goal submit form.
 */

import React, { useContext } from "react";

import { Tab, Tabs, TabList, TabPanel } from 'react-tabs';

import { useForm } from "react-hook-form";

import ActionFormContext from "./ActionFormContext";

const textBoxClassName = "block box-border w-full rounded mt-1 mb-2 text-sm "
    + "border border-slate-500 p-2 font-mono";
const submitClassName = "bg-slate-900 text-white hover:bg-slate-800 "
    + "dark:bg-slate-200 dark:text-slate-900 dark:hover:bg-slate-100 "
    + "active:scale-95 inline-flex items-center justify-center "
    + "rounded-md text-sm font-medium transition-colors "
    + "focus:outline-none focus:ring-2 focus:ring-slate-400 "
    + "focus:ring-offset-2 dark:focus:ring-slate-400 "
    + "disabled:pointer-events-none dark:focus:ring-offset-slate-900 "
    + "disabled:bg-slate-400 disabled:dark:bg-slate-400 h-10 py-2 px-4 "
    + "cursor-pointer"

const ActionForm = ({ sendMessage }) => {
    const custom = useForm();
    const onSubmitCustom = (data) => {
        custom.reset();
        sendMessage(JSON.stringify(
            {
                form: "custom",
                formData: data
            }
        ));
    };

    const generated = useForm();
    const actionFormContext = useContext(ActionFormContext);
    const generateForm = () => {
        return (
            actionFormContext.length > 0 ?
                actionFormContext.slice(1).map((param, index) => {
                    return (
                        <div key={index}>
                            <label className="font-mono">{param}</label>
                            <input type="text" {...generated.register(param)}
                                className={textBoxClassName} required />
                        </div>
                    );
                })
                : <div className="py-2">No action selected.</div>
        );
    };
    const onSubmitGenerated = (data) => {
        generated.reset();
        for (let [key, value] of Object.entries(data)) {
            if (!actionFormContext.includes(key) || value === "") {
                delete data[key];
            }
        }

        let array: string[] = [actionFormContext[0]]
        for (let i = 1; i < actionFormContext.length; i++) {
            array.push(data[actionFormContext[i]]);
        }
        sendMessage(JSON.stringify(
            {
                form: "generated",
                formData: array
            }
        ));
    };

    return (
        <div className="flex flex-col gap-1 w-full h-full">
            <form
                onSubmit={custom.handleSubmit(onSubmitCustom)}
                className="flex flex-col mx-5 my-4"
            >
                <label className="text-lg">Custom Action</label>
                <textarea {...custom.register("custom")}
                    className={textBoxClassName} required />

                <input type="submit" value="Submit"
                    // From Button.tsx
                    className={submitClassName}
                />
            </form>

            <div className="separator text-slate-500 text-center italic">
                ——— OR ———
            </div>

            <form
                onSubmit={generated.handleSubmit(onSubmitGenerated)}
                className="flex flex-col mx-5 my-4"
            >
                <label className="text-lg">
                    Selected Action
                    {/* Looks a bit weird in code but I want to make only
                    part of the label monospace */}
                    {
                        actionFormContext.length > 0
                            ? ": "
                            : ""
                    }
                    {
                        actionFormContext.length > 0
                            ? <span className="font-mono">
                                {actionFormContext[0]}
                            </span>
                            : ""
                    }
                </label>

                {generateForm()}

                {
                    actionFormContext.length > 0
                        ?
                        <>
                            <label className="text-sm pt-2 pb-2">
                                All fields are required.
                            </label>
                            <input type="submit" value="Submit"
                                // From Button.tsx
                                className={submitClassName}
                            />
                        </>
                        : null
                }
            </form>
        </div>
    );
};

const GoalForm = ({ sendMessage }) => {
    const { register, handleSubmit, reset, formState: { errors } } = useForm();
    const onSubmitGoal = (data) => {
        reset();
        sendMessage(JSON.stringify(
            {
                form: "goal",
                formData: data
            }
        ));
    };

    return (
        <form
            onSubmit={handleSubmit(onSubmitGoal)}
            className="flex flex-col m-5"
        >
            <label>Agent</label>
            <input type="text" defaultValue="self" {...register("agent")}
                className={textBoxClassName} required />

            <label>Goal</label>
            <input type="text" {...register("goal")}
                className={textBoxClassName} required />

            <label className="text-sm pt-2 pb-2">
                All fields are required.
            </label>

            <input type="submit" value="Submit"
                // From Button.tsx
                className={submitClassName}
            />
        </form>
    );
};

const ActionGoalForm = ({ sendMessage }) => {
    return (
        <div className="p-5">
            <Tabs>
                <TabList>
                    <Tab>Submit Action</Tab>
                    <Tab>Submit Goal</Tab>
                </TabList>

                <TabPanel>
                    <ActionForm sendMessage={sendMessage} />
                </TabPanel>

                <TabPanel>
                    <GoalForm sendMessage={sendMessage} />
                </TabPanel>
            </Tabs>
        </div>
    );
};

export default ActionGoalForm;