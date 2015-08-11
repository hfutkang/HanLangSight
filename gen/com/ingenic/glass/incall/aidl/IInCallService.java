/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/sc-kang/GlassM200/ingenicM200/packages/apps/HanLangSight/src/com/ingenic/glass/incall/aidl/IInCallService.aidl
 */
package com.ingenic.glass.incall.aidl;
public interface IInCallService extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.ingenic.glass.incall.aidl.IInCallService
{
private static final java.lang.String DESCRIPTOR = "com.ingenic.glass.incall.aidl.IInCallService";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.ingenic.glass.incall.aidl.IInCallService interface,
 * generating a proxy if needed.
 */
public static com.ingenic.glass.incall.aidl.IInCallService asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.ingenic.glass.incall.aidl.IInCallService))) {
return ((com.ingenic.glass.incall.aidl.IInCallService)iin);
}
return new com.ingenic.glass.incall.aidl.IInCallService.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_setAudioMode:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.setAudioMode(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_registerInCallListener:
{
data.enforceInterface(DESCRIPTOR);
com.ingenic.glass.incall.aidl.IInCallListener _arg0;
_arg0 = com.ingenic.glass.incall.aidl.IInCallListener.Stub.asInterface(data.readStrongBinder());
long _result = this.registerInCallListener(_arg0);
reply.writeNoException();
reply.writeLong(_result);
return true;
}
case TRANSACTION_unregisterInCallListener:
{
data.enforceInterface(DESCRIPTOR);
long _arg0;
_arg0 = data.readLong();
this.unregisterInCallListener(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.ingenic.glass.incall.aidl.IInCallService
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
@Override public void setAudioMode(int mode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(mode);
mRemote.transact(Stub.TRANSACTION_setAudioMode, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public long registerInCallListener(com.ingenic.glass.incall.aidl.IInCallListener l) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
long _result;
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeStrongBinder((((l!=null))?(l.asBinder()):(null)));
mRemote.transact(Stub.TRANSACTION_registerInCallListener, _data, _reply, 0);
_reply.readException();
_result = _reply.readLong();
}
finally {
_reply.recycle();
_data.recycle();
}
return _result;
}
@Override public void unregisterInCallListener(long id) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeLong(id);
mRemote.transact(Stub.TRANSACTION_unregisterInCallListener, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_setAudioMode = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_registerInCallListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_unregisterInCallListener = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
}
public void setAudioMode(int mode) throws android.os.RemoteException;
public long registerInCallListener(com.ingenic.glass.incall.aidl.IInCallListener l) throws android.os.RemoteException;
public void unregisterInCallListener(long id) throws android.os.RemoteException;
}
