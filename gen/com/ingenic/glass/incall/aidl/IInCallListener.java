/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: /home/sc-kang/GlassM200/ingenicM200/packages/apps/HanLangSight/src/com/ingenic/glass/incall/aidl/IInCallListener.aidl
 */
package com.ingenic.glass.incall.aidl;
public interface IInCallListener extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements com.ingenic.glass.incall.aidl.IInCallListener
{
private static final java.lang.String DESCRIPTOR = "com.ingenic.glass.incall.aidl.IInCallListener";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an com.ingenic.glass.incall.aidl.IInCallListener interface,
 * generating a proxy if needed.
 */
public static com.ingenic.glass.incall.aidl.IInCallListener asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof com.ingenic.glass.incall.aidl.IInCallListener))) {
return ((com.ingenic.glass.incall.aidl.IInCallListener)iin);
}
return new com.ingenic.glass.incall.aidl.IInCallListener.Stub.Proxy(obj);
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
case TRANSACTION_onPreModeStateChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.onPreModeStateChanged(_arg0);
reply.writeNoException();
return true;
}
case TRANSACTION_onPostModeStateChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.onPostModeStateChanged(_arg0);
reply.writeNoException();
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements com.ingenic.glass.incall.aidl.IInCallListener
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
@Override public void onPreModeStateChanged(int mode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(mode);
mRemote.transact(Stub.TRANSACTION_onPreModeStateChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
@Override public void onPostModeStateChanged(int mode) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
android.os.Parcel _reply = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(mode);
mRemote.transact(Stub.TRANSACTION_onPostModeStateChanged, _data, _reply, 0);
_reply.readException();
}
finally {
_reply.recycle();
_data.recycle();
}
}
}
static final int TRANSACTION_onPreModeStateChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_onPostModeStateChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
}
public void onPreModeStateChanged(int mode) throws android.os.RemoteException;
public void onPostModeStateChanged(int mode) throws android.os.RemoteException;
}
