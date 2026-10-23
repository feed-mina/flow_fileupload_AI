import React,{useEffect,useState} from 'react';
import {Alert,Button,Input} from 'antd';
import {getSession,login,logout} from './lib/api.js';
export default function SessionPanel(){
  const [session,setSession]=useState(null),[name,setName]=useState(''),[password,setPassword]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  useEffect(()=>{let live=true;getSession().then(s=>{if(live)setSession(s);}).catch(()=>{});return()=>{live=false;};},[]);
  if(!session?.loginAvailable)return null;
  async function submit(e){e.preventDefault();if(busy)return;setBusy(true);setError('');try{await login(name,password);setPassword('');setSession(await getSession());}catch(failure){setError(failure.message);}finally{setBusy(false);}}
  async function exit(){setBusy(true);try{await logout();setSession(await getSession());}catch(failure){setError(failure.message);}finally{setBusy(false);}}
  return <section className="session-panel" aria-label="데모 로그인">
    {session.authenticated?<><span>{session.username} 로그인 상태</span><Button onClick={exit} disabled={busy}>로그아웃</Button></>:<form onSubmit={submit}><label htmlFor="login-name">아이디</label><Input id="login-name" value={name} autoComplete="username" onChange={e=>setName(e.target.value)} disabled={busy}/><label htmlFor="login-password">비밀번호</label><Input.Password id="login-password" value={password} autoComplete="current-password" onChange={e=>setPassword(e.target.value)} disabled={busy}/><Button htmlType="submit" disabled={busy||!name||!password}>로그인</Button></form>}
    {error&&<Alert type="error" role="alert" title={error}/>}
  </section>;
}
